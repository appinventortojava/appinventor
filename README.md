appinventor
===========

This version and all versions of App Inventor have two parts, the web interface and a build server (that packages apks, etc.)

To download a copy, click on the download Zip icon at the top left hand side

Prerequisites: 
Apache ant: 1.8. 1 or 1.8.2 Type “ant -version” at a shell to check your version
Java
Bash
Google AppEngine Java SDK.   Get the Java version of the SDK from https://developers.google.com/appengine/downloads.

Note: You will need to modify the appengine-web.xml file according to run a local copy or deploy to appspot. The file is located in " appinventor/appengine/war/ "
You will need to edit the "build.server.host" property line, located towards the end of the file and change its value either to:
"localhost:9990" To run a local instance 
"appinventor.cs.usfca.edu:9990" (or what ever server you have the build server running on at port 9990) If you are deploying to appspot 

How to run a local version of the Web Interface:

Open a terminal window 
Cd into the appinventor folder on your local machine
Compile the code by running the command "ant"
Once complied you can launch the web interface with the following command 
"<the path to your appengine SDK folder>/bin/dev_appserver.sh --port=8888 --address=0.0.0.0 appengine/build/war/"
Then start a local instance of the build server

How to run a local version of the build server

In another terminal window:
cd into the appinventor folder on your local machine then into the buildserver folder
Run the command "ant RunLocalBuildServer"
once you get confirmation go to a web browser and run,
"localhost:8888" and start using our version of appinventor

To Deploy to appspot: 

Open a terminal window 
Cd into the appinventor folder on your local machine
The compile the code by running the command "ant"
You can then deploy to appspot with the following command "<your-appengine-SDK-folder>/bin/appcfg.sh -A <your-application-id> update appinventor/appengine/build/war"

To Update the Build Server at USF

The you will need another machine/server to run the buildServer
to deploy that you will need to Cd into appinventor/buildserver 
Run the command " ant BuildDeploymentTar " once complete there will be a buildserver.tar file in appinventor/build/buildserver folder
You will need to take the file untar it, and upload all the jar files to the buildserver machine,  (appinventor.cs.usfca.edu) and place the all the jars in the lib folder that was just extracted into "/usr/shared/buildserver", replacing the old ones.  And then run the commands below to stop and start server to restart the buildserver with the new version.

Start command : sudo /etc/init.d/android_buildserver start 
Stop command :  sudo /etc/init.d/android_buildserver stop 

Commonly edited Files:

For the build server:
Projectbuilder (where the parser/code generator is called) appinventor/buildserver/src/com/google/appinventor/buildserver/PrjectBuilder.java

To update the about page:
Show about command ( for about page ) appinventor/appengine/src/com/google/appinventor/client/explorer/commands/ShowAboutCommnad.java
Design toolbar ( for adding the drop down menu to download java file etc ) appinventor/appengine/src/com/google/appinventor/client/DesignToolbar.java

NOTE: If you would like to contribute to this project please email David Wolber at wolberd@usfca.edu 
Also when committing updates to GIT hub please be sure to run the "ant clean" command in the root of the appinventor folder and in the buildserver folder 
