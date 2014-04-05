/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.rhq.coregui.client.admin.agent.install;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.ExpansionMode;
import com.smartgwt.client.util.BooleanCallback;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.HTMLFlow;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.fields.ButtonItem;
import com.smartgwt.client.widgets.form.fields.CanvasItem;
import com.smartgwt.client.widgets.form.fields.CheckboxItem;
import com.smartgwt.client.widgets.form.fields.HeaderItem;
import com.smartgwt.client.widgets.form.fields.PasswordItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.form.fields.TextItem;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.layout.HLayout;
import com.smartgwt.client.widgets.layout.Layout;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.install.remote.AgentInstall;
import org.rhq.core.domain.install.remote.AgentInstallInfo;
import org.rhq.core.domain.install.remote.AgentInstallStep;
import org.rhq.core.domain.install.remote.RemoteAccessInfo;
import org.rhq.core.domain.measurement.MeasurementUnits;
import org.rhq.coregui.client.CoreGUI;
import org.rhq.coregui.client.IconEnum;
import org.rhq.coregui.client.components.view.ViewName;
import org.rhq.coregui.client.gwt.GWTServiceLookup;
import org.rhq.coregui.client.gwt.RemoteInstallGWTServiceAsync;
import org.rhq.coregui.client.util.ErrorHandler;
import org.rhq.coregui.client.util.MeasurementConverterClient;
import org.rhq.coregui.client.util.enhanced.EnhancedIButton;
import org.rhq.coregui.client.util.enhanced.EnhancedVLayout;
import org.rhq.coregui.client.util.message.Message;

/**
 * @author Greg Hinkle
 */
public class RemoteAgentInstallView extends EnhancedVLayout {
    public static final ViewName VIEW_ID = new ViewName("RemoteAgentInstall",
        MSG.view_adminTopology_remoteAgentInstall(), IconEnum.AGENT);

    private RemoteInstallGWTServiceAsync remoteInstallService = GWTServiceLookup.getRemoteInstallService(600000);

    private DynamicForm connectionForm;
    private Layout buttonsForm;
    private EnhancedIButton installButton;
    private EnhancedIButton uninstallButton;
    private EnhancedIButton startButton;
    private EnhancedIButton stopButton;
    private ButtonItem findAgentInstallPathButton;
    private ButtonItem statusCheckButton;
    private CheckboxItem rememberMeCheckbox;
    private VLayout agentInfoLayout;

    private final boolean showInstallButton;
    private final boolean showUninstallButton;
    private final boolean showStartButton;
    private final boolean showStopButton;

    private AgentInstall initialAgentInstall;

    public RemoteAgentInstallView(AgentInstall initialInfo, boolean showInstallButton, boolean showUninstallButton,
        boolean showStartButton, boolean showStopButton) {

        super();
        this.initialAgentInstall = initialInfo;
        this.showInstallButton = showInstallButton;
        this.showUninstallButton = showUninstallButton;
        this.showStartButton = showStartButton;
        this.showStopButton = showStopButton;
        setMembersMargin(1);
        setWidth100();
        setHeight100();
    }

    @Override
    protected void onInit() {
        super.onInit();
        Layout layout = new VLayout();
        layout.setPadding(10);
        HTMLFlow header = new HTMLFlow(MSG.view_remoteAgentInstall_connInfo());
        header.setStyleName("headerItem");
        header.setExtraSpace(5);
        layout.addMember(header);
        layout.addMember(getConnectionForm());
        header = new HTMLFlow("");
        header.setStyleName("headerItem");
        header.setExtraSpace(5);
        layout.addMember(header);
        layout.addMember(getButtons());

        agentInfoLayout = new VLayout();
        agentInfoLayout.setWidth100();
        agentInfoLayout.setHeight100();
        agentInfoLayout.setMembersMargin(1);
        layout.addMember(agentInfoLayout);
        addMember(layout);

    }

    private DynamicForm getConnectionForm() {
        connectionForm = new DynamicForm();
        connectionForm.setNumCols(4);
        connectionForm.setWrapItemTitles(false);
        connectionForm.setColWidths("130", "450", "110");
        connectionForm.setExtraSpace(15);
        final int textFieldWidth = 440;

        TextItem host = new TextItem("host", MSG.common_title_host());
        host.setRequired(true);
        host.setWidth(textFieldWidth);
        host.setPrompt(MSG.view_remoteAgentInstall_promptHost());
        host.setHoverWidth(300);
        host.setEndRow(true);

        TextItem port = new TextItem("port", MSG.common_title_port());
        port.setRequired(false);
        port.setWidth(textFieldWidth);
        port.setPrompt(MSG.view_remoteAgentInstall_promptPort());
        port.setHoverWidth(300);
        port.setEndRow(true);

        TextItem username = new TextItem("username", MSG.common_title_user());
        username.setRequired(true);
        username.setWidth(textFieldWidth);
        username.setPrompt(MSG.view_remoteAgentInstall_promptUser());
        username.setHoverWidth(300);
        username.setEndRow(true);

        PasswordItem password = new PasswordItem("password", MSG.common_title_password());
        password.setRequired(false);
        password.setWidth(textFieldWidth);
        password.setPrompt(MSG.view_remoteAgentInstall_promptPassword());
        password.setHoverWidth(300);
        password.setEndRow(true);

        rememberMeCheckbox = new CheckboxItem("rememberme", MSG.view_remoteAgentInstall_rememberMe());
        rememberMeCheckbox.setRequired(false);
        rememberMeCheckbox.setPrompt(MSG.view_remoteAgentInstall_promptRememberMe());
        rememberMeCheckbox.setHoverWidth(300);
        rememberMeCheckbox.setColSpan(2);
        rememberMeCheckbox.setEndRow(true);

        TextItem agentInstallPath = new TextItem("agentInstallPath", MSG.view_remoteAgentInstall_installPath());
        agentInstallPath.setWidth(textFieldWidth);
        agentInstallPath.setPrompt(MSG.view_remoteAgentInstall_promptInstallPath());
        agentInstallPath.setHoverWidth(300);
        agentInstallPath.setStartRow(true);
        agentInstallPath.setEndRow(false);

        findAgentInstallPathButton = new ButtonItem("findAgentInstallPathButton",
            MSG.view_remoteAgentInstall_buttonFindAgent());
        findAgentInstallPathButton.setStartRow(false);
        findAgentInstallPathButton.setEndRow(true);
        if (findAgentInstallPathButton.getTitle().length() < 15) { //i18n may prolong the title
            findAgentInstallPathButton.setWidth(100);
        }
        findAgentInstallPathButton.addClickHandler(new com.smartgwt.client.widgets.form.fields.events.ClickHandler() {
            public void onClick(com.smartgwt.client.widgets.form.fields.events.ClickEvent clickEvent) {
                findAgentInstallPath();
            }
        });

        StaticTextItem agentStatus = new StaticTextItem("agentStatus", MSG.view_remoteAgentInstall_agentStatus());
        agentStatus.setDefaultValue(MSG.view_remoteAgentInstall_agentStatusDefault());
        agentStatus.setRedrawOnChange(true);
        agentStatus.setRedrawOnChange(true);
        agentStatus.setStartRow(true);
        agentStatus.setEndRow(false);

        statusCheckButton = new ButtonItem("updateStatus", MSG.view_remoteAgentInstall_updateStatus());
        statusCheckButton.setStartRow(false);
        statusCheckButton.setEndRow(true);
        if (findAgentInstallPathButton.getTitle().length() < 15) { //i18n may prolong the title
            statusCheckButton.setWidth(100);
        }
        statusCheckButton.addClickHandler(new com.smartgwt.client.widgets.form.fields.events.ClickHandler() {
            public void onClick(com.smartgwt.client.widgets.form.fields.events.ClickEvent clickEvent) {
                if (connectionForm.validate()) {
                    agentStatusCheck();
                }
            }
        });

        if (initialAgentInstall != null) {
            host.setValue(initialAgentInstall.getSshHost());
            if (initialAgentInstall.getSshPort() != null) {
                port.setValue(String.valueOf(initialAgentInstall.getSshPort()));
            }
            username.setValue(initialAgentInstall.getSshUsername());
            password.setValue(initialAgentInstall.getSshPassword());
            agentInstallPath.setValue(initialAgentInstall.getInstallLocation());
        }

        connectionForm.setFields(host, port, username, password, rememberMeCheckbox, agentInstallPath,
            findAgentInstallPathButton, agentStatus, statusCheckButton);

        return connectionForm;
    }

    private Layout getButtons() {
        buttonsForm = new HLayout();

        installButton = new EnhancedIButton(MSG.view_remoteAgentInstall_installAgent());
        installButton.setExtraSpace(10);
        installButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent clickEvent) {
                if (connectionForm.validate()) {
                    installAgent();
                }
            }
        });

        uninstallButton = new EnhancedIButton(MSG.view_remoteAgentInstall_uninstallAgent());
        uninstallButton.setExtraSpace(10);
        uninstallButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent clickEvent) {
                if (connectionForm.validate()) {
                    uninstallAgent();
                }
            }
        });

        startButton = new EnhancedIButton(MSG.view_remoteAgentInstall_startAgent());
        startButton.setExtraSpace(10);
        startButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent clickEvent) {
                if (connectionForm.validate()) {
                    startAgent();
                }
            }
        });

        stopButton = new EnhancedIButton(MSG.view_remoteAgentInstall_stopAgent());
        stopButton.setExtraSpace(10);
        stopButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent clickEvent) {
                if (connectionForm.validate()) {
                    stopAgent();
                }
            }
        });

        ArrayList<Canvas> buttons = new ArrayList<Canvas>(3);
        if (this.showInstallButton) {
            buttons.add(installButton);
        }
        if (this.showUninstallButton && initialAgentInstall != null && initialAgentInstall.getAgentName() != null) {
            buttons.add(uninstallButton); // note we only show this if we were given the agent name because that is required to be known to uninstall
        }
        if (this.showStartButton) {
            buttons.add(startButton);
        }
        if (this.showStopButton) {
            buttons.add(stopButton);
        }

        if (buttons.size() > 0) {
            buttonsForm.setAlign(Alignment.CENTER);
            buttonsForm.setMembers(buttons.toArray(new Canvas[buttons.size()]));
        }

        return buttonsForm;
    }

    private void displayError(String msg, Throwable caught) {
        CoreGUI.getErrorHandler().handleError(msg, caught);
        String rootCause = ErrorHandler.getRootCauseMessage(caught);
        String fullMsg = (rootCause == null) ? msg : msg + ": " + rootCause;
        SC.warn(fullMsg);
    }

    private void displayMessage(String msg) {
        CoreGUI.getMessageCenter().notify(
            new Message(msg, Message.Severity.Info, EnumSet.of(Message.Option.BackgroundJobResult)));
    }

    private void findAgentInstallPath() {
        final Map<String, String> errors = new HashMap<String, String>(2);
        if (connectionForm.getValueAsString("host") == null
            || connectionForm.getValueAsString("host").trim().isEmpty()) {
            errors.put("host", CoreGUI.getSmartGwtMessages().validator_requiredField());
        }
        if (connectionForm.getValueAsString("username") == null
            || connectionForm.getValueAsString("username").trim().isEmpty()) {
            errors.put("username", CoreGUI.getSmartGwtMessages().validator_requiredField());
        }
        connectionForm.setErrors(errors, true);
        if (errors.isEmpty()) {
            disableButtons(true);

            final String parentPath = connectionForm.getValueAsString("agentInstallPath");

            remoteInstallService.findAgentInstallPath(getRemoteAccessInfo(), parentPath, new AsyncCallback<String>() {
                public void onFailure(Throwable caught) {
                    disableButtons(false);
                    displayError(MSG.view_remoteAgentInstall_error_1(), caught);
                }

                public void onSuccess(String result) {
                    disableButtons(false);
                    if (result != null) {
                        connectionForm.setValue("agentInstallPath", result);
                    } else {
                        String err;
                        if (parentPath == null || parentPath.length() == 0) {
                            err = MSG.view_remoteAgentInstall_error_2();
                        } else {
                            err = MSG.view_remoteAgentInstall_error_3(parentPath);
                        }
                        displayError(err, null);
                    }
                    agentStatusCheck();
                }
            });
        }
    }

    private void agentStatusCheck() {
        disableButtons(true);
        remoteInstallService.agentStatus(getRemoteAccessInfo(), getAgentInstallPath(), new AsyncCallback<String>() {
            public void onFailure(Throwable caught) {
                disableButtons(false);
                connectionForm.setValue("agentStatus", caught.getMessage());
            }

            public void onSuccess(String result) {
                disableButtons(false);
                connectionForm.setValue("agentStatus", result);
            }
        });
    }

    private void installAgent() {
        disableButtons(true);
        connectionForm.setValue("agentStatus", "Installing, this may take a few minutes...");

        // FOR TESTING WITHOUT DOING A REAL INSTALL - START
        //        AgentInstallInfo result = new AgentInstallInfo("mypath", "myown", "1.1", "localHOST", "serverHOST");
        //        for (int i = 1; i < 20; i++)
        //            result.addStep(new AgentInstallStep("cmd" + i, "desc" + i, i, "result" + i, i * 10));
        //        for (Canvas child : agentInfoLayout.getChildren())
        //            child.destroy();
        //        buildInstallInfoCanvas(agentInfoLayout, result);
        //        agentInfoLayout.markForRedraw();
        //        disableButtons(false);
        //        if (true)
        //            return;
        // FOR TESTING WITHOUT DOING A REAL INSTALL - END

        SC.ask(MSG.view_remoteAgentInstall_overwriteAgentTitle(), MSG.view_remoteAgentInstall_overwriteAgentQuestion(),
            new BooleanCallback() {
                @Override
                public void execute(Boolean overwriteExistingAgent) {
                    remoteInstallService.installAgent(getRemoteAccessInfo(), getAgentInstallPath(),
                        overwriteExistingAgent.booleanValue(), new AsyncCallback<AgentInstallInfo>() {
                            public void onFailure(Throwable caught) {
                                disableButtons(false);
                                displayError(MSG.view_remoteAgentInstall_error_4(), caught);
                                connectionForm.setValue("agentStatus", MSG.view_remoteAgentInstall_error_4());
                            }

                            public void onSuccess(AgentInstallInfo result) {
                                disableButtons(false);
                                displayMessage(MSG.view_remoteAgentInstall_success());
                                connectionForm.setValue("agentStatus", MSG.view_remoteAgentInstall_success());

                                for (Canvas child : agentInfoLayout.getChildren()) {
                                    child.destroy();
                                }
                                buildInstallInfoCanvas(agentInfoLayout, result);
                                agentInfoLayout.markForRedraw();
                                agentStatusCheck();
                            }
                        });
                }
            });
    }

    private void uninstallAgent() {
        disableButtons(true);

        remoteInstallService.uninstallAgent(getRemoteAccessInfo(), new AsyncCallback<String>() {
            public void onFailure(Throwable caught) {
                disableButtons(false);
                displayError(MSG.view_remoteAgentInstall_error_7(), caught);
                connectionForm.setValue("agentStatus", MSG.view_remoteAgentInstall_error_7());
            }

            public void onSuccess(String result) {
                disableButtons(false);
                if (result != null) {
                    connectionForm.setValue("agentStatus", MSG.view_remoteAgentInstall_uninstallSuccess());
                    displayMessage(MSG.view_remoteAgentInstall_uninstallAgentResults(result));
                    agentStatusCheck();
                } else {
                    connectionForm.setValue("agentStatus", MSG.view_remoteAgentInstall_error_7());
                }
            }
        });
    }

    private void startAgent() {
        disableButtons(true);
        remoteInstallService.startAgent(getRemoteAccessInfo(), getAgentInstallPath(), new AsyncCallback<String>() {
            public void onFailure(Throwable caught) {
                disableButtons(false);
                displayError(MSG.view_remoteAgentInstall_error_5(), caught);
            }

            public void onSuccess(String result) {
                disableButtons(false);
                displayMessage(MSG.view_remoteAgentInstall_startAgentResults(result));
                agentStatusCheck();
            }
        });
    }

    private void stopAgent() {
        disableButtons(true);
        remoteInstallService.stopAgent(getRemoteAccessInfo(), getAgentInstallPath(), new AsyncCallback<String>() {
            public void onFailure(Throwable caught) {
                disableButtons(false);
                displayError(MSG.view_remoteAgentInstall_error_6(), caught);
            }

            public void onSuccess(String result) {
                disableButtons(false);
                displayMessage(MSG.view_remoteAgentInstall_stopAgentResults(result));
                agentStatusCheck();
            }
        });
    }

    private void buildInstallInfoCanvas(VLayout installInfo, AgentInstallInfo info) {
        DynamicForm infoForm = new DynamicForm();
        infoForm.setMargin(20);
        infoForm.setWidth100();
        infoForm.setHeight100();

        HeaderItem infoHeader = new HeaderItem();
        infoHeader.setValue(MSG.view_remoteAgentInstall_installInfo());

        StaticTextItem version = new StaticTextItem("version", MSG.common_title_version());
        version.setValue(info.getVersion());

        StaticTextItem path = new StaticTextItem("path", MSG.common_title_path());
        path.setValue(info.getPath());

        StaticTextItem owner = new StaticTextItem("owner", MSG.view_remoteAgentInstall_owner());
        owner.setValue(info.getOwner());

        StaticTextItem config = new StaticTextItem("config", MSG.common_title_configuration());
        config.setValue(info.getConfigurationStartString());

        CanvasItem listCanvas = new CanvasItem();
        listCanvas.setShowTitle(false);
        listCanvas.setColSpan(2);

        VLayout listLayout = new VLayout(0);
        listLayout.setWidth100();
        listLayout.setHeight100();

        ListGrid listGrid = new ListGrid() {
            @Override
            protected Canvas getExpansionComponent(ListGridRecord record) {
                Canvas canvas = super.getExpansionComponent(record);
                canvas.setPadding(5);
                return canvas;
            }
        };
        listGrid.setWidth100();
        listGrid.setHeight100();
        listGrid.setCanExpandRecords(true);
        listGrid.setExpansionMode(ExpansionMode.DETAIL_FIELD);
        listGrid.setDetailField("result");
        ListGridField step = new ListGridField("description", MSG.view_remoteAgentInstall_step());
        ListGridField result = new ListGridField("result", MSG.view_remoteAgentInstall_result());
        ListGridField resultCode = new ListGridField("resultCode", MSG.view_remoteAgentInstall_resultCode(), 90);
        ListGridField duration = new ListGridField("duration", MSG.common_title_duration(), 90);
        listGrid.setFields(step, result, resultCode, duration);
        listGrid.setData(getStepRecords(info));

        listLayout.addMember(listGrid);
        listCanvas.setCanvas(listLayout);

        infoForm.setFields(infoHeader, version, path, owner, config, listCanvas);
        installInfo.addMember(infoForm);

        return;
    }

    private ListGridRecord[] getStepRecords(AgentInstallInfo info) {
        ArrayList<ListGridRecord> steps = new ArrayList<ListGridRecord>();

        for (AgentInstallStep step : info.getSteps()) {
            ListGridRecord rec = new ListGridRecord();
            rec.setAttribute("description", step.getDescription());
            String result = step.getResult();
            if (result == null || result.trim().length() == 0) {
                result = MSG.view_remoteAgentInstall_resultCode() + "=" + step.getResultCode();
            }
            rec.setAttribute("result", result);
            rec.setAttribute("resultCode", "" + step.getResultCode());
            rec.setAttribute("duration",
                MeasurementConverterClient.format((double) step.getDuration(), MeasurementUnits.MILLISECONDS, true));
            steps.add(rec);
        }

        return steps.toArray(new ListGridRecord[steps.size()]);
    }

    private void disableButtons(boolean disabled) {
        installButton.setDisabled(disabled);
        uninstallButton.setDisabled(disabled);
        startButton.setDisabled(disabled);
        stopButton.setDisabled(disabled);
        buttonsForm.setDisabled(disabled);
        findAgentInstallPathButton.setDisabled(disabled);
        statusCheckButton.setDisabled(disabled);
    }

    private RemoteAccessInfo getRemoteAccessInfo() {
        String host = connectionForm.getValueAsString("host");
        String port = connectionForm.getValueAsString("port");
        String username = connectionForm.getValueAsString("username");
        String password = connectionForm.getValueAsString("password");

        int portInt;
        try {
            portInt = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            portInt = 22;
        }
        connectionForm.setValue("port", portInt);

        RemoteAccessInfo info = new RemoteAccessInfo(host, portInt, username, password);

        if (this.initialAgentInstall != null) {
            info.setAgentName(this.initialAgentInstall.getAgentName());
        }

        boolean rememberme = Boolean.parseBoolean(connectionForm.getValueAsString("rememberme"));
        info.setRememberMe(rememberme);

        return info;
    }

    private String getAgentInstallPath() {
        if (connectionForm.getValueAsString("agentInstallPath") == null
            || connectionForm.getValueAsString("agentInstallPath").trim().isEmpty()) {
            findAgentInstallPath();
        }
        return connectionForm.getValueAsString("agentInstallPath");
    }
}
