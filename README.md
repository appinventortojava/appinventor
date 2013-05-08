appinventor
===========

This version and all versions of App Inventor have two parts, the web interface and a build server (that packages apks, etc.)

Running a local version of Web Interface:

Cd into the appinventor folder
Complile the code by running "ant"
Once complied you can launch the web interface with the following command 
"<your appengine SDK folder>/appengine-java-sdk-1.6.4.1/bin/dev_appserver.sh --port=8888 --address=0.0.0.0 appengine/build/war/"

BUILD SERVER

In another terminal window:
cd into appinventor/buildserver
Run the command "ant RunLocalBuildServer"
once you get confirmation go to a web browser and run,
"localhost:8888" and start using our version of appinventor

To Deploy to appspot: 

Cd into the appinventor folder
The complile the code by running "ant"
You can then deploy to appspot with the following command "<your-appengine-SDK-folder>/bin/appcfg.sh -A <your-application-id> update appinventor/appengine/build/war"
The you will need another machine/server to run the buildServer
to deploy that you will need to Cd into appinventor/buildserver 
Run the command " ant BuildDeploymentTar " once complete there will be a buildserver.tar file in app inventor/build/buildserver  
You will need to take file untar it, and upload all the jar files to the buildserver machine,  (appinventor.cs.usfca.edu) and place the lib folder that is extracted into /usr/shared/buildserver.  And then run the command stop and start to restart the buildserver with the new files.

Start command : sudo /etc/init.d/android_buildserver start 
Stop command :  sudo /etc/init.d/android_buildserver stop 

Files mailny used for the build server:
Projectbuilder

To update the about page:
Show about command ( for about page )
Design toolbar ( for adding the drop down menu to download java file etc )
