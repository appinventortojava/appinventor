// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.client.explorer.commands;

import com.google.common.base.Preconditions;
import com.google.appinventor.client.utils.Downloader;
import com.google.appinventor.shared.rpc.ServerLayout;
import com.google.appinventor.shared.rpc.project.ProjectNode;

/**
 * Command for downloading a project as Eclipse project with Java source files
 *
 * <p/>This command is often chained with SaveAllEditorsCommand and BuildCommand.
 *
 */
public class DownloadAsEclipseProjectCommand extends ChainableCommand {
  // The download target
  private String target;
  private int buildOption = 1;

  /**
   * Creates a new command for downloading a project target.
   *
   * @param target the target to be downloaded (must be non-null,
   *               use "" if there is no particular target)
   */
  public DownloadAsEclipseProjectCommand(String target) {
    // Since we don't know when the download is finished, we can't support a
    // command after this one.
    super(null); // no next command
    Preconditions.checkNotNull(target);
    this.target = target;
  }

	public DownloadAsEclipseProjectCommand(String target, int buildOption) {
		this(target);
		this.buildOption = buildOption;
	}

  
  @Override
  public boolean willCallExecuteNextCommand() {
    return false;
  }

  @Override
  public void execute(ProjectNode node) {
	  if (buildOption == 1)
		  Downloader.getInstance().download(ServerLayout.DOWNLOAD_SERVLET_BASE +
				  ServerLayout.DOWNLOAD_AS_ECLIPSE_PROJECT + "/" + node.getProjectId() + "/" + target);
	  else 
		  Downloader.getInstance().download(ServerLayout.DOWNLOAD_SERVLET_BASE +
				  ServerLayout.DOWNLOAD_AS_JAVA_SOURCES + "/" + node.getProjectId() + "/" + target);
		  
  }
}
