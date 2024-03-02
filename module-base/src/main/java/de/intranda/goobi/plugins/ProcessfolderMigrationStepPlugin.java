package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;

@PluginImplementation
@Log4j2
public class ProcessfolderMigrationStepPlugin implements IStepPluginVersion2 {
    private static final long serialVersionUID = -2510181076944758334L;
	@Getter
    private String title = "intranda_step_processfolder_migration";
    @Getter
    private Step step;
    private SubnodeConfiguration myconfig;
    private String returnPath;
    private static String regex;
    
    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_processfolder_migration.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }
    
    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }
    
    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = true;
        try {
        	StorageProviderInterface sp = StorageProvider.getInstance();
        	// prepare the VariableReplacer
        	Fileformat ff = step.getProzess().readMetadataFile();
        	VariableReplacer replacer = new VariableReplacer(ff.getDigitalDocument(), 
        			step.getProzess().getRegelsatz().getPreferences(), 
        			step.getProzess(), null);
        	
        	// run through the configuration rules
        	List<HierarchicalConfiguration> rule = myconfig.configurationsAt("rule");
            for (HierarchicalConfiguration node : rule) {
            	String action = node.getString("@action");
            	String source = node.getString("@source");
    			String target = node.getString("@target");
    			
    			// check minimum parameters
    			if (source == null) {
    				throw new IOException("Parameter 'source' cannot be null");
    			}
    			Path sourcePath = Paths.get(step.getProzess().getProcessDataDirectory(), replacer.replace(source));
    			
    			// based on the action parameter execute the right commands
    			if (action.equals("delete")) {
    			
    				// in case the path shall be deleted
					regex = sourcePath.getFileName().toString();
					String parent = sourcePath.getParent().toAbsolutePath().toString();
					List<Path> files = sp.listFiles(parent, deletionFilter);
					for (Path f : files) {
      					sp.deleteDir(f);
					}
    			} else if (action.equals("create")) {
    			
    				// in case the path shall be created as folder
    				sp.createDirectories(sourcePath);
    			} else if (action.equals("move")) {
    				
    				// in case the path shall be renamed/moved the target parameter needs to be there
    				if (target == null) {
    					throw new IOException("Parameter 'target' cannot be null for action 'move'");
    				}
    				// do the actual renaming if target folder does not exist already or is empty
    				Path renamedPath = Paths.get(step.getProzess().getProcessDataDirectory(), replacer.replace(target));
    				if (sp.isFileExists(sourcePath) && (!sp.isFileExists(renamedPath) || sp.getNumberOfFiles(renamedPath, "") == 0)) {
    					sp.move(sourcePath,renamedPath);
    				}
    			} else if (action.equals("copy")) {
    				
    				// in case the path shall be copied the target parameter needs to be there
    				if (target == null) {
    					throw new IOException("Parameter 'target' cannot be null for action 'copy'");
    				}
    				// do the actual copying if target folder does not exist already or is empty
    				Path copiedPath = Paths.get(step.getProzess().getProcessDataDirectory(), replacer.replace(target));
    				if (sp.isFileExists(sourcePath) && (!sp.isFileExists(copiedPath) || sp.getNumberOfFiles(copiedPath, "") == 0)) {
    					sp.copyDirectory(sourcePath,copiedPath);
    				}
    			} else {
    				
    				// if the action is unknown throw an exception
    				throw new IOException("Parameter 'action' is not valid: " + action);
    			}
    		}
        	
        } catch (IOException | SwapException | PreferencesException | ReadException e) {
        	// in case of errors write it into the process log and into the log file
        	successful = false;
//        	Helper.addMessageToProcessJournal(step.getProcessId(), LogType.ERROR,
//                    "Plugin for folder migration in step '" + step.getTitel() + "' reported an error:" + e.getMessage());
        	log.error("PLUGIN FOLDER MIGRATION: Error occured", e);
        }
        
        // finish the plugin
        log.info("ProcessfolderMigration step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }
    
    public static final DirectoryStream.Filter<Path> deletionFilter = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path path) throws IOException {
            String name = path.getFileName().toString();
            if (name.matches(regex)) {
                return true;
            }
            return false;
        }
    };
}
