/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap;

import com.bc.ceres.core.CoreException;
import com.bc.ceres.core.runtime.ConfigurationElement;
import com.bc.ceres.core.runtime.Extension;
import com.bc.ceres.core.runtime.ExtensionPoint;
import com.bc.ceres.core.runtime.Module;
import com.bc.ceres.core.runtime.ModuleContext;
import com.bc.ceres.core.runtime.ModuleState;
import com.sun.java.help.search.QueryEngine;
import org.esa.snap.framework.help.HelpSys;
import org.esa.snap.framework.ui.application.ApplicationDescriptor;
import org.esa.snap.framework.ui.application.ToolViewDescriptor;
import org.esa.snap.framework.ui.application.ToolViewDescriptorRegistry;
import org.esa.snap.framework.ui.command.Command;
import org.esa.snap.framework.ui.command.CommandGroup;
import org.esa.snap.framework.ui.layer.LayerEditorDescriptor;
import org.esa.snap.framework.ui.layer.LayerSourceDescriptor;
import org.esa.snap.util.TreeNode;

import javax.help.HelpSet;
import javax.help.HelpSet.DefaultHelpSetFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The activator for the BEAM UI module. Registers help set extensions.
 *
 * @author Marco Peters
 * @author Norman Fomferra
 * @version $Revision$ $Date$
 */
public class BeamUiActivator implements ToolViewDescriptorRegistry {

    private static BeamUiActivator instance = new BeamUiActivator();
    private ModuleContext moduleContext;
    private TreeNode<HelpSet> helpSetRegistry;
    private List<Command> actionList;
    private List<CommandGroup> actionGroupList;
    private Map<String, ToolViewDescriptor> toolViewDescriptorRegistry;
    private Map<String, LayerSourceDescriptor> layerSourcesRegistry;
    private ApplicationDescriptor applicationDescriptor;
    private int helpSetNo;

    public void start(ModuleContext moduleContext) throws CoreException {
        this.moduleContext = moduleContext;
        instance = this;
        registerHelpSets(moduleContext);
        registerToolViews(moduleContext);
        registerActions(moduleContext);
        registerActionGroups(moduleContext);
        registerApplicationDescriptors(moduleContext);
        registerLayerEditors(moduleContext);
        registerLayerSources(moduleContext);
    }

    public void stop(ModuleContext moduleContext) throws CoreException {
        this.helpSetRegistry = null;
        this.moduleContext = null;
        actionList = null;
        toolViewDescriptorRegistry = null;
        applicationDescriptor = null;
        instance = null;
    }

    private void registerApplicationDescriptors(ModuleContext moduleContext) {
        final List<ApplicationDescriptor> applicationDescriptorList = BeamCoreActivator.loadExecutableExtensions(moduleContext, "applicationDescriptors", "applicationDescriptor", ApplicationDescriptor.class);
        final String applicationId = getApplicationId();
        for (ApplicationDescriptor applicationDescriptor : applicationDescriptorList) {
            if (applicationId.equals(applicationDescriptor.getApplicationId())) {
                moduleContext.getLogger().info(String.format("Using application descriptor [%s]", applicationId));
                this.applicationDescriptor = applicationDescriptor;
                final String[] toolViewIds = applicationDescriptor.getExcludedToolViews();
                for (String toolViewId : toolViewIds) {
                    BeamUiActivator.getInstance().removeToolViewDescriptor(toolViewId);
                    moduleContext.getLogger().info(String.format("Removed toolview [%s]", toolViewId));
                }
                final String[] actionIds = applicationDescriptor.getExcludedActions();
                for (String actionId : actionIds) {
                    BeamUiActivator.getInstance().removeAction(actionId);
                    moduleContext.getLogger().info(String.format("Removed action [%s]", actionId));
                }
                final String[] actionGroupIds = applicationDescriptor.getExcludedActionGroups();
                for (String actionGroupId : actionGroupIds) {
                    BeamUiActivator.getInstance().removeActionGroup(actionGroupId);
                    moduleContext.getLogger().info(String.format("Removed action group [%s]", actionGroupId));
                }
            } else {
                moduleContext.getLogger().warning(String.format("Ignoring application descriptor [%s]", applicationId));
            }
        }
    }

    public static BeamUiActivator getInstance() {
        return instance;
    }

    public ModuleContext getModuleContext() {
        return moduleContext;
    }

    public String getApplicationId() {
        return moduleContext.getRuntimeConfig().getApplicationId();
    }

    public ApplicationDescriptor getApplicationDescriptor() {
        return applicationDescriptor;
    }

    public List<Command> getCommands() {
        return Collections.unmodifiableList(actionList);
    }

    public List<CommandGroup> getCommandGroups() {
        return Collections.unmodifiableList(actionGroupList);
    }

    @Override
    public ToolViewDescriptor[] getToolViewDescriptors() {
        return toolViewDescriptorRegistry.values().toArray(new ToolViewDescriptor[toolViewDescriptorRegistry.values().size()]);
    }

    @Override
    public ToolViewDescriptor getToolViewDescriptor(String viewDescriptorId) {
        return toolViewDescriptorRegistry.get(viewDescriptorId);
    }

    public LayerSourceDescriptor[] getLayerSources() {
        return layerSourcesRegistry.values().toArray(
                new LayerSourceDescriptor[layerSourcesRegistry.values().size()]);
    }

    public void removeToolViewDescriptor(String viewDescriptorId) {
        toolViewDescriptorRegistry.remove(viewDescriptorId);
    }

    public void removeAction(String actionId) {
        for (int i = 0; i < actionList.size(); i++) {
            Command command = actionList.get(i);
            if (actionId.equals(command.getCommandID())) {
                actionList.remove(i);
                return;
            }
        }
    }

    public void removeActionGroup(String actionGroupId) {
        for (int i = 0; i < actionGroupList.size(); i++) {
            Command command = actionGroupList.get(i);
            if (actionGroupId.equals(command.getCommandID())) {
                actionGroupList.remove(i);
                return;
            }
        }
    }


    private void registerToolViews(ModuleContext moduleContext) {
        List<ToolViewDescriptor> toolViewDescriptorList = BeamCoreActivator.loadExecutableExtensions(moduleContext,
                                                                                                     "toolViews",
                                                                                                     "toolView",
                                                                                                     ToolViewDescriptor.class);
        toolViewDescriptorRegistry = new HashMap<>(2 * toolViewDescriptorList.size());
        for (ToolViewDescriptor toolViewDescriptor : toolViewDescriptorList) {
            final String toolViewId = toolViewDescriptor.getId();
            final ToolViewDescriptor existingViewDescriptor = toolViewDescriptorRegistry.get(toolViewId);
            if (existingViewDescriptor != null) {
                moduleContext.getLogger().info(String.format("Tool view [%s] has been redeclared!\n", toolViewId));
            }
            toolViewDescriptorRegistry.put(toolViewId, toolViewDescriptor);
        }
    }

    private void registerActions(ModuleContext moduleContext) {
        actionList = BeamCoreActivator.loadExecutableExtensions(moduleContext,
                                                                "actions",
                                                                "action",
                                                                Command.class);
        HashMap<String, Command> actionMap = new HashMap<>(2 * actionList.size() + 1);
        for (Command action : new ArrayList<>(actionList)) {
            final String actionId = action.getCommandID();
            final Command existingAction = actionMap.get(actionId);
            if (existingAction != null) {
                moduleContext.getLogger().warning(String.format("Action [%s] has been redeclared!\n", actionId));
                actionMap.remove(actionId);
                actionList.remove(existingAction);
            }
            actionMap.put(actionId, action);
        }
    }


    private void registerActionGroups(ModuleContext moduleContext) {
        actionGroupList = BeamCoreActivator.loadExecutableExtensions(moduleContext,
                                                                     "actionGroups",
                                                                     "actionGroup",
                                                                     CommandGroup.class);
        HashMap<String, CommandGroup> actionGroupMap = new HashMap<>(2 * actionGroupList.size() + 1);
        for (CommandGroup actionGroup : new ArrayList<>(actionGroupList)) {
            final String actionGroupId = actionGroup.getCommandID();
            final CommandGroup existingActionGroup = actionGroupMap.get(actionGroupId);
            if (existingActionGroup != null) {
                moduleContext.getLogger().warning(String.format("Action group [%s] has been redeclared!\n", actionGroupId));
                actionGroupMap.remove(actionGroupId);
                actionGroupList.remove(existingActionGroup);
            }
            actionGroupMap.put(actionGroupId, actionGroup);
        }
    }


    private void registerLayerEditors(ModuleContext moduleContext) {
        BeamCoreActivator.loadExecutableExtensions(moduleContext,
                                                   "layerEditors",
                                                   "layerEditor",
                                                   LayerEditorDescriptor.class);
    }

    private void registerLayerSources(ModuleContext moduleContext) {
        List<LayerSourceDescriptor> layerSourceListDescriptor =
                BeamCoreActivator.loadExecutableExtensions(moduleContext,
                                                           "layerSources",
                                                           "layerSource",
                                                           LayerSourceDescriptor.class);
        layerSourcesRegistry = new HashMap<>(2 * layerSourceListDescriptor.size());
        for (LayerSourceDescriptor layerSourceDescriptor : layerSourceListDescriptor) {
            final String id = layerSourceDescriptor.getId();
            final LayerSourceDescriptor existingLayerSourceDescriptor = layerSourcesRegistry.get(id);
            if (existingLayerSourceDescriptor
                    != null) {
                moduleContext.getLogger().info(String.format("Layer source [%s] has been redeclared!\n", id));
            }
            layerSourcesRegistry.put(id, layerSourceDescriptor);
        }
    }

    private void registerHelpSets(ModuleContext moduleContext) {
        this.helpSetRegistry = new TreeNode<>("");

        ExtensionPoint hsExtensionPoint = moduleContext.getModule().getExtensionPoint("helpSets");
        Extension[] hsExtensions = hsExtensionPoint.getExtensions();
        for (Extension extension : hsExtensions) {
            ConfigurationElement confElem = extension.getConfigurationElement();
            ConfigurationElement[] helpSetElements = confElem.getChildren("helpSet");
            for (ConfigurationElement helpSetElement : helpSetElements) {
                final Module declaringModule = extension.getDeclaringModule();
                if (declaringModule.getState().is(ModuleState.RESOLVED)) {
                    registerHelpSet(helpSetElement, declaringModule);
                }
            }
        }

        addNodeToHelpSys(helpSetRegistry);
    }

    private void addNodeToHelpSys(TreeNode<HelpSet> helpSetNode) {
        if (helpSetNode.getContent() != null) {
            HelpSys.add(helpSetNode.getContent());
        }
        TreeNode<HelpSet>[] children = helpSetNode.getChildren();
        for (TreeNode<HelpSet> child : children) {
            addNodeToHelpSys(child);
        }
    }

    private void registerHelpSet(ConfigurationElement helpSetElement, Module declaringModule) {
        String helpSetPath = null;

        ConfigurationElement pathElem = helpSetElement.getChild("path");
        if (pathElem != null) {
            helpSetPath = pathElem.getValue();
        }
        // todo - remove
        if (helpSetPath == null) {
            helpSetPath = helpSetElement.getAttribute("path");
        }
        if (helpSetPath == null) {
            String message = String.format("Missing resource [path] element in a help set declared in module [%s].",
                                           declaringModule.getName());
            moduleContext.getLogger().severe(message);
            return;
        }

        URL helpSetUrl = declaringModule.getClassLoader().getResource(helpSetPath);
        if (helpSetUrl == null) {
            String message = String.format("Help set resource path [%s] of module [%s] not found.",
                                           helpSetPath, declaringModule.getName());
            moduleContext.getLogger().severe(message);
            return;
        }

        DefaultHelpSetFactory factory = new VerifyingHelpSetFactory(helpSetPath, declaringModule.getName(), moduleContext.getLogger());
        HelpSet helpSet = HelpSet.parse(helpSetUrl, declaringModule.getClassLoader(), factory);

        if (helpSet == null) {
            String message = String.format("Failed to add help set [%s] of module [%s]: %s.",
                                           helpSetPath, declaringModule.getName(),
                                           "");
            moduleContext.getLogger().log(Level.SEVERE, message, "");
            return;
        }

        String helpSetId;
        ConfigurationElement idElem = helpSetElement.getChild("id");
        if (idElem != null) {
            helpSetId = idElem.getValue();
        } else {
            helpSetId = "helpSet$" + helpSetNo;
            helpSetNo++;

            String message = String.format("Missing [id] element in help set [%s] of module [%s].",
                                           helpSetPath,
                                           declaringModule.getSymbolicName());
            moduleContext.getLogger().warning(message);
        }

        String helpSetParent;
        ConfigurationElement parentElem = helpSetElement.getChild("parent");
        if (parentElem != null) {
            helpSetParent = parentElem.getValue();
        } else {
            helpSetParent = ""; // = root
        }

        TreeNode<HelpSet> parentNode = helpSetRegistry.createChild(helpSetParent);
        TreeNode<HelpSet> childNode = parentNode.getChild(helpSetId);
        if (childNode == null) {
            childNode = new TreeNode<>(helpSetId, helpSet);
            parentNode.addChild(childNode);
        } else if (childNode.getContent() == null) {
            childNode.setContent(helpSet);
        } else {
            String message = String.format("Help set ignored: Duplicate identifier [%s] in help set [%s] of module [%s] ignored.",
                                           helpSetId,
                                           helpSetPath,
                                           declaringModule.getName());
            moduleContext.getLogger().severe(message);
        }
    }

    private static class VerifyingHelpSetFactory extends DefaultHelpSetFactory {
        private final String helpSetPath;
        private final String moduleName;
        private final Logger logger;

        public VerifyingHelpSetFactory(String helpSetPath, String moduleName, Logger logger) {
            super();
            this.helpSetPath = helpSetPath;
            this.moduleName = moduleName;
            this.logger = logger;
        }

        @Override
        public void processView(HelpSet hs,
                                String name,
                                String label,
                                String type,
                                Hashtable viewAttributes,
                                String data,
                                Hashtable dataAttributes,
                                Locale locale) {
            if (name.equals("Search")) {
                // check if a search engine can be created, this means the search index is available
                try {
                    // just for checking if it can be created
                    QueryEngine qe = new QueryEngine(data, hs.getHelpSetURL());
                } catch (Exception exception) {
                    String message = String.format("Help set [%s] of module [%s] has no or bad search index. Search view removed.",
                                                   helpSetPath, moduleName);
                    logger.log(Level.SEVERE, message, "");
                    return;
                }
            }
            super.processView(hs, name, label, type, viewAttributes, data, dataAttributes, locale);
        }
    }

}
