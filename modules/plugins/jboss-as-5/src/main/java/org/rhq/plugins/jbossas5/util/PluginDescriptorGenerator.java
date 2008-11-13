/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.plugins.jbossas5.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jboss.deployers.spi.management.KnownComponentTypes;
import org.jboss.deployers.spi.management.KnownDeploymentTypes;
import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.ManagedComponent;

import org.rhq.core.clientapi.descriptor.DescriptorPackages;
import org.rhq.core.clientapi.descriptor.configuration.ConfigurationDescriptor;
import org.rhq.core.clientapi.descriptor.configuration.SimpleProperty;
import org.rhq.core.clientapi.descriptor.configuration.PropertyType;
import org.rhq.core.clientapi.descriptor.configuration.ConfigurationProperty;
import org.rhq.core.clientapi.descriptor.configuration.ListProperty;
import org.rhq.core.clientapi.descriptor.configuration.MapProperty;
import org.rhq.core.clientapi.descriptor.plugin.MetricDescriptor;
import org.rhq.core.clientapi.descriptor.plugin.OperationDescriptor;
import org.rhq.core.clientapi.descriptor.plugin.PluginDescriptor;
import org.rhq.core.clientapi.descriptor.plugin.ResourceDescriptor;
import org.rhq.core.clientapi.descriptor.plugin.ServiceDescriptor;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.configuration.definition.PropertyDefinition;
import org.rhq.core.domain.configuration.definition.PropertySimpleType;
import org.rhq.core.domain.configuration.definition.PropertyDefinitionSimple;
import org.rhq.core.domain.configuration.definition.PropertyDefinitionList;
import org.rhq.core.domain.configuration.definition.PropertyDefinitionMap;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.operation.OperationDefinition;
import org.rhq.core.domain.resource.ResourceType;

/**
 * @author Ian Springer
 */
public class PluginDescriptorGenerator {
    private static final Log LOG = LogFactory.getLog(PluginDescriptorGenerator.class);    

    private static final QName SIMPLE_PROPERTY_QNAME = new QName(RhqNamespacePrefixMapper.CONFIGURATION_NAMESPACE, "simple-property");
    private static final QName LIST_PROPERTY_QNAME = new QName(RhqNamespacePrefixMapper.CONFIGURATION_NAMESPACE, "list-property");
    private static final QName MAP_PROPERTY_QNAME = new QName(RhqNamespacePrefixMapper.CONFIGURATION_NAMESPACE, "map-property");

    private static Map<PropertySimpleType, PropertyType> TYPE_MAP = new HashMap();
    static {
        TYPE_MAP.put(PropertySimpleType.BOOLEAN, PropertyType.BOOLEAN);
        TYPE_MAP.put(PropertySimpleType.DIRECTORY, PropertyType.DIRECTORY);
        TYPE_MAP.put(PropertySimpleType.DOUBLE, PropertyType.DOUBLE);
        TYPE_MAP.put(PropertySimpleType.FILE, PropertyType.FILE);
        TYPE_MAP.put(PropertySimpleType.FLOAT, PropertyType.FLOAT);
        TYPE_MAP.put(PropertySimpleType.INTEGER, PropertyType.INTEGER);
        TYPE_MAP.put(PropertySimpleType.LONG, PropertyType.LONG);
        TYPE_MAP.put(PropertySimpleType.LONG_STRING, PropertyType.LONG_STRING);
        TYPE_MAP.put(PropertySimpleType.PASSWORD, PropertyType.PASSWORD);
        TYPE_MAP.put(PropertySimpleType.STRING, PropertyType.STRING);
    }

    public static void generatePluginDescriptor(ManagementView managementView, File tempDir) throws Exception {
        PluginDescriptor pluginDescriptor = new PluginDescriptor();
        Set<ComponentType> knownComponentTypes = getKnownComponentTypes();
        for (ComponentType componentType : knownComponentTypes) {
            Set<ManagedComponent> components = managementView.getComponentsForType(componentType);
            if (components == null || components.isEmpty()) {
                LOG.warn("No components of type [" + componentType + "] found.");
                continue;
            }
            // Use the first one as a representative sample of the type.
            ManagedComponent component = components.iterator().next();
            // First convert ManagedComponent to ResourceType...
            ResourceType resourceType = MetadataConversionUtils.convertComponentToResourceType(component);
            // Then convert ResourceType to JAXB ServiceDescriptor object.
            convertAndMergeResourceType(pluginDescriptor, resourceType);
        }
        File tempFile = File.createTempFile("rhq-plugin", ".xml", tempDir);
        writeToFile(pluginDescriptor, tempFile);
        Set<String> knownDeploymentTypeNames = getKnownDeploymentTypeNames();
        // TODO: Anything we can do with deployment types?
    }

    private static void convertAndMergeResourceType(PluginDescriptor pluginDescriptor, ResourceType resourceType) {
        List<ServiceDescriptor> services = pluginDescriptor.getServices();
        ServiceDescriptor serviceDescriptor = new ServiceDescriptor();
        serviceDescriptor.setName(resourceType.getName());
        if (resourceType.isSingleton())
            serviceDescriptor.setSingleton(resourceType.isSingleton());
        // TODO: Handle remaining fields.
        convertAndMergeOperationDefinitions(resourceType, serviceDescriptor);
        convertAndMergeMetricDefinitions(resourceType, serviceDescriptor);
        convertAndMergeResourceConfiguration(resourceType, serviceDescriptor);
        services.add(serviceDescriptor);
    }

    private static void convertAndMergeResourceConfiguration(ResourceType resourceType, ServiceDescriptor serviceDescriptor) {
        ConfigurationDefinition resourceConfigDef = resourceType.getResourceConfigurationDefinition();
        ConfigurationDescriptor resourceConfigDescriptor = convertConfigurationDefinitionToConfigurationDescriptor(
                resourceConfigDef);
        serviceDescriptor.setResourceConfiguration(resourceConfigDescriptor);
    }

    private static void convertAndMergeMetricDefinitions(ResourceType resourceType, ServiceDescriptor serviceDescriptor) {
        List<MetricDescriptor> metricDescriptors = serviceDescriptor.getMetric();
        for (MeasurementDefinition metricDef : resourceType.getMetricDefinitions()) {
        MetricDescriptor metricDescriptor = new MetricDescriptor();
            metricDescriptors.add(metricDescriptor);
            if (metricDef.getCategory() != null)
                metricDescriptor.setCategory(metricDef.getCategory().name().toLowerCase());
            metricDescriptor.setDataType(metricDef.getDataType().name().toLowerCase());
            metricDescriptor.setDefaultInterval((int)metricDef.getDefaultInterval());
            metricDescriptor.setDefaultOn(metricDef.isDefaultOn());
            metricDescriptor.setDescription(metricDef.getDescription());
            metricDescriptor.setDestinationType(metricDef.getDestinationType());
            metricDescriptor.setDisplayName(metricDef.getDisplayName());
            if (metricDef.getDisplayType() != null)
                metricDescriptor.setDisplayType(metricDef.getDisplayType().name().toLowerCase());
            if (metricDef.getNumericType() != null)
                metricDescriptor.setMeasurementType(metricDef.getNumericType().name().toLowerCase());
            metricDescriptor.setProperty(metricDef.getName());
            //metricDescriptor.setUnits(metricDef.getUnits()); // TODO
        }
    }

    private static void convertAndMergeOperationDefinitions(ResourceType resourceType, ResourceDescriptor resourceDescriptor) {
        List<OperationDescriptor> operationDescriptors = resourceDescriptor.getOperation();
        for (OperationDefinition opDef : resourceType.getOperationDefinitions()) {
            OperationDescriptor operationDescriptor = new OperationDescriptor();
            operationDescriptors.add(operationDescriptor);
            operationDescriptor.setDescription(opDef.getDescription());
            operationDescriptor.setDisplayName(opDef.getDisplayName());
            operationDescriptor.setName(opDef.getName());
            ConfigurationDefinition paramsConfigDef = opDef.getParametersConfigurationDefinition();
            ConfigurationDescriptor paramsConfigDescriptor = convertConfigurationDefinitionToConfigurationDescriptor(paramsConfigDef);
            operationDescriptor.setParameters(paramsConfigDescriptor);
            ConfigurationDefinition resultsConfigDef = opDef.getResultsConfigurationDefinition();
            ConfigurationDescriptor resultsConfigDescriptor = convertConfigurationDefinitionToConfigurationDescriptor(resultsConfigDef);
            operationDescriptor.setResults(resultsConfigDescriptor);
            operationDescriptor.setTimeout(opDef.getTimeout());
        }
    }

    private static ConfigurationDescriptor convertConfigurationDefinitionToConfigurationDescriptor(
            ConfigurationDefinition configDef) {
        if (configDef == null)
            return null;
        ConfigurationDescriptor configDescriptor = new ConfigurationDescriptor();
        configDescriptor.setNotes(configDef.getDescription());
        for (PropertyDefinition propDef : configDef.getPropertyDefinitions().values()) {
            ConfigurationProperty configProp = convertPropertyDefinitionToConfigurationProperty(propDef);
            JAXBElement propElement = getJAXBElement(configProp);
            configDescriptor.getConfigurationProperty().add(propElement);
        }
        return configDescriptor;
    }

    private static ConfigurationProperty convertPropertyDefinitionToConfigurationProperty(PropertyDefinition propDef) {
        ConfigurationProperty configProp;
        if (propDef instanceof PropertyDefinitionSimple) {
            PropertyDefinitionSimple propDefSimple = (PropertyDefinitionSimple)propDef;
            SimpleProperty simpleProp = new SimpleProperty();
            simpleProp.setDefaultValue(propDefSimple.getDefaultValue());
            simpleProp.setDefaultValueDescription(propDefSimple.getDefaultValue());
            simpleProp.setInitialValue(propDefSimple.getDefaultValue());
            if (propDefSimple.getType() != PropertySimpleType.STRING)
                simpleProp.setType(TYPE_MAP.get(propDefSimple.getType()));
            configProp = simpleProp;
        } else if (propDef instanceof PropertyDefinitionList) {
            PropertyDefinitionList propDefList = (PropertyDefinitionList)propDef;
            ListProperty listProp = new ListProperty();
            //listProp.setMin(BigInteger.valueOf(propDefList.getMin()));
            //listProp.setMax(String.valueOf(propDefList.getMax()));
            ConfigurationProperty memberConfigProp = convertPropertyDefinitionToConfigurationProperty(
                    propDefList.getMemberDefinition());
            JAXBElement propElement = getJAXBElement(memberConfigProp);
            listProp.setConfigurationProperty(propElement);
            configProp = listProp;
        } else if (propDef instanceof PropertyDefinitionMap) {
            PropertyDefinitionMap propDefMap = (PropertyDefinitionMap)propDef;
            MapProperty mapProp = new MapProperty();
            for (PropertyDefinition itemPropDef : propDefMap.getPropertyDefinitions().values()) {
                ConfigurationProperty itemConfigProp = convertPropertyDefinitionToConfigurationProperty(itemPropDef);
                JAXBElement propElement = getJAXBElement(itemConfigProp);
                mapProp.getConfigurationProperty().add(propElement);
            }
            configProp = mapProp;
        } else {
            throw new IllegalStateException("Invalid property definition type: " + propDef.getClass().getName());
        }
        configProp.setDescription(propDef.getDescription());
        //simpleProp.setActivationPolicy(propDefSimple.getActivationPolicy()); // TODO
        configProp.setName(propDef.getName());
        //simpleProp.setPropertyOptions(); // TODO
        if (propDef.isReadOnly())
            configProp.setReadOnly(propDef.isReadOnly());
        if (!propDef.isRequired())
            configProp.setRequired(propDef.isRequired());
        if (propDef.isSummary())
            configProp.setSummary(propDef.isSummary());
        //simpleProp.setUnits(propDefSimple.getUnits()); // TODO
        return configProp;
    }

    private static JAXBElement<? extends ConfigurationProperty> getJAXBElement(ConfigurationProperty configProp) {
        JAXBElement<? extends ConfigurationProperty> propElement;
        QName qname;
        if (configProp instanceof SimpleProperty)
            qname = SIMPLE_PROPERTY_QNAME;
        else if (configProp instanceof ListProperty)
            qname = LIST_PROPERTY_QNAME;
        else if (configProp instanceof MapProperty)
            qname = MAP_PROPERTY_QNAME;
        else
            throw new IllegalStateException();
        propElement = new JAXBElement(qname, configProp.getClass(), configProp);
        return propElement;
    }

    private static Set<String> getKnownDeploymentTypeNames() {
        Set<String> knownDeploymentTypeNames = new LinkedHashSet();
        for (KnownDeploymentTypes type : KnownDeploymentTypes.values()) {
            knownDeploymentTypeNames.add(type.getType());
        }
        return knownDeploymentTypeNames;
    }

    private static Set<ComponentType> getKnownComponentTypes() {
        Set<ComponentType> knownComponentTypes = new LinkedHashSet<ComponentType>();
        for (KnownComponentTypes.ConnectionFactoryTypes type : KnownComponentTypes.ConnectionFactoryTypes.values()) {
            knownComponentTypes.add(type.getType());
        }
        for (KnownComponentTypes.DataSourceTypes type : KnownComponentTypes.DataSourceTypes.values()) {
            knownComponentTypes.add(type.getType());
        }
        for (KnownComponentTypes.EJB type : KnownComponentTypes.EJB.values()) {
            knownComponentTypes.add(type.getType());
        }
        for (KnownComponentTypes.JMSDestination type : KnownComponentTypes.JMSDestination.values()) {
            knownComponentTypes.add(type.getType());
        }
        return knownComponentTypes;
    }

    private static void writeToFile(PluginDescriptor pluginDescriptor,
                                    File file)
            throws Exception {
        LOG.info("Writing plugin descriptor to [" + file + "]...");
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
        JAXBContext jaxbContext = JAXBContext.newInstance(DescriptorPackages.PC_PLUGIN);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new RhqNamespacePrefixMapper());
        marshaller.marshal(pluginDescriptor, outputStream);
        outputStream.close();
    }
}
