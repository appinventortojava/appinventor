package com.google.appinventor.client.explorer.commands;

import com.google.appinventor.client.Ode;
import static com.google.appinventor.client.Ode.MESSAGES;
import com.google.appinventor.client.output.OdeLog;
import com.google.appinventor.shared.rpc.ServerLayout;
import com.google.appinventor.shared.rpc.project.ProjectNode;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Command for displaying a About for the target of a project.
 *
 * <p/>This command is often chained with SaveAllEditorsCommand and BuildCommand.
 *
 * @author markf@google.com (Mark Friedman)
 */
public class ShowAboutCommand extends ChainableCommand {
  private static final String CHARTSERVER_About_URI =
      "http://chart.apis.google.com/chart?cht=qr&chs=200x200&chl=";

  // The build target
  private String target;

  /**
   * Creates a new command for showing a About for the target of a project.
   *
   * @param target the build target
   */
  public ShowAboutCommand(String target) {
    // Since we don't know when the About dialog is finished, we can't
    // support a command after this one.
    super(null); // no next command
    this.target = target;
  }

  @Override
  public boolean willCallExecuteNextCommand() {
    return false;
  }

  @Override
  public void execute(final ProjectNode node) {
    // Display a About for an url pointing at our server's download servlet
    new AboutDialogBox(node.getName()).center();
  }
static class AboutDialogBox extends DialogBox {

    AboutDialogBox(String projectName) {
      super(false, true);
      setStylePrimaryName("ode-DialogBox");
      setText("About");

      ClickHandler buttonHandler = new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          hide();
        }
      };
      Button okButton = new Button(MESSAGES.okButton());
      okButton.addClickHandler(buttonHandler);
      HorizontalPanel buttonPanel = new HorizontalPanel();
      buttonPanel.setHorizontalAlignment(HorizontalPanel.ALIGN_CENTER);
      HTML warningLabel = new HTML("<p>About: </p>" +
        "<p> App Inventor to Java is an exploratory version of App Inventor which allows one to "+
        "generate a Java program equivalent for an App Inventor app. The generated code uses the Java" +
        " Bridge library, code created by Google and now managed by the MIT App Inventor team. </p> " +

        "<p>This tool is still under development. It will generate valid Java code for many apps, but will " +
        "break on others. We encourage you to use the tool and appreciate any feedback you can provide us "+
        "at the Google group <a href=\"https://groups.google.com/forum/?fromgroups#!forum/usfappinvtojava\" target=\"_blank\">usfappinvtojava</a></p> " +

        "<p> The following tutorial apps from <a href=\"http://www.appinventor.org/\" target=\"_blank\" >appinventor.org</a> "+
        "have been tested and generate valid Java code: </p>" +

        "- Hello Purr <br />" +
        "- Map Tour <br />" +
        "<br /> <p> The following work, after casting issues are resolved: </p>" +
        "- Mole Mash <br />" +
        "- Paint Pot <br />" +
        "- No Texting While Driving <br />" +


        "<p> You can generate either Java files or an entire Eclipse project, which includes a link "+
        "to the Java Bridge Library.</p>" +
        
        "<p>Known Limitations: <br />"+
        " - Location aware applications not yet supported<br />"+
        " - Complex data structures (lists of complex items) not fully supported <br />"+
        " - A nest list will take the type of the last element. All nested list will need to be of the same type. <br />" +
        " - You will need to cast to specific types <br /> " +

        "<p> Please post your feedback and bugs/limitations to the Google group " +
        "<a href=\"https://groups.google.com/forum/?fromgroups#!forum/usfappinvtojava\" target=\"_blank\">usfappinvtojava</a> </p>" +

        "App Inventor to Java is being developed by the <a href=\"http://democratizecomputing.org\" target=\"_blank\">Democratize "+
        "Computing Lab</a> at the University of San Francisco. The project is under the direction of Professor " +
        "David Wolber.</p>"+ 

        "<p> USF Contributors include: </p>" +
        "Edric Orense <br /> " +
        "Aaron Halblieb <br /> " +
        "Michael Silvestri <br /> " +
        "Ramya Gadiyaram <br /> " +
        "John Galyardt" +



        "<p>The project has built upon previous work by Jeff Gray’s group at the University of Alabama. </p>");
       warningLabel.setWordWrap(true);
       warningLabel.setWidth("700px");  // set width to get the text to wrap
       HorizontalPanel warningPanel = new HorizontalPanel();
       warningPanel.setHorizontalAlignment(HorizontalPanel.ALIGN_LEFT);
       warningPanel.add(warningLabel);
      buttonPanel.add(okButton);
      buttonPanel.setSize("100%", "24px");
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.add(warningPanel);
      contentPanel.add(buttonPanel);
      add(contentPanel);
      }
        
  }
}