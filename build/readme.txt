HOW TO BUILD

--------------------------------------------------------------------------
PREREQUISITES

Before you can build the SSOFI Provider war file, you will need to 
have a Java JDK installed for the compiler, and you will need
ANT installed to run the build.

That is the only requirement.

We try to maintain the build guildlines documented in:
http://agiletribe.wordpress.com/2013/07/16/designing-the-build/


--------------------------------------------------------------------------
SETUP

(1) You must create a build folder on a disk that you can write
to and that all the output of the build to be stored.   Create the folder
anywhere, with any name, but remember the full path and use that path
anytime these instructions use the term <buildfolder>.

It is worth noting that the build will write only to this folder and 
to folders under this folder.  It will NEVER write any output to the source
directories, so you don't have to worry about your source folders
becoming corrupted, or unecessarily changed by the build.  THis allows
the source to be on a read-only device like a CD-ROM, or on a read-only
network drive, without preventing the ability to make a build.  It is also
easy to archive the results of a build separately from the source.


(2) Copy from the build folder:  build.bat and build_configuration.example.bat.
Rename build_configuration.example.bat to build_configuration.bat.


(3) Edit the "build_configuration.bat" to enter the values there.  
Read the instructions in that file on exactly what values need to be entered
there -- it is only a few critical paths to where things are installed
and where the output should be written.

The "build_configuration.bat" is the only file that needs to be modified for
your particular system.  The rest of the build uses this file for settings.


--------------------------------------------------------------------------
RUNNING THE BUILD


All you have to do on Windows is to run "build.bat".    We don't at this time
have build scripts for other operating systems.


--------------------------------------------------------------------------
