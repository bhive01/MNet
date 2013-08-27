MNet
====

ImageJ Plugin

The purpose of this plugin is to find netting on the surface of a melon fruit in order to segment it away from sutures or the ground color. 

INSTALL
=======

To install the plug-in copy the Netting_[DateofCompile].jar to ImageJ's plug-ins directory.


RUN
===
The filter can be accessed in Process > Filters > Melon Imaging > Netting

To invoke the filter from a macro use the following:

run("Netting", "angle=45 radius=20 sensitivity=10 background=20 suture=80 netting=110");


RELEASE NOTES
=============

---- Release 0.0.1 - 2013-08-21
---- Release 0.0.2 - 2013-08-27

+ Initial prototype
