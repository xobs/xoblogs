---
layout: post
category : sdcard
tags : [falconwing, linux, kernel, booting]
---

Before we get into the mechanics of the bootloader, we want to make sure
that there's a glimmer of hope for our board being supported.  Once we've
established that, we can duplicate a similar device, come up with a machine
ID for our board, compile a new kernel, and then figure out how to load the
new kernel image.

Obtaining the kernel
--------------------
The primary, latest, bleeding-edge kernel is called linux-next.  You can
[browse the
repository](http://git.kernel.org/?p=linux/kernel/git/next/linux-next.git;a=summary)
over HTTP, but we'll want to check it out to develop locally.  On your
development machine, run the following to pull down a copy of the kernel:

    git clone git://git.kernel.org/pub/scm/linux/kernel/git/next/linux-next.git

The kernel is made up of about 700 megabytes of patches, commit notes, and
documentation.  It takes a long time to collect the files and send them
down the wire.  A common suggestion is to go grab a coffee.  Or bake a
cake, and then eat it.  I've seen it take ten hours to download over a slow
connection, but fifteen minutes is a more reasonable estimate.

Verifying processor support
---------------------------

The very first step in bringing up Linux on a new ARM platform is to make
sure your processor is supported.  A large chunk of the arch/arm/ directory
is dedicated to various architectures, so chances are very good that at
least the core IRQ and memory manager is supported.  If not, you've got a
much harder road ahead of you.

By simply using "grep", I found my processor -- the i.MX233 -- goes by the
architecture name "mxs" in Linux:

    smc@build-ssd:~/linux/arch/arm$ grep -ir mx23 mach-* | cut -d/ -f1 | uniq -c
          6 mach-mmp
        678 mach-mxs
    smc@build-ssd:~/linux/arch/arm$ 

The mention of the machine in *mach-mmp* is probably more of a fluke, as
speaking from experience I know that to be a Marvell platform.  However,
further investigation (i.e. poking around the files in that directory)
reveals that mach-mxs does, in fact, support the i.MX233 processor.

Registering our machine
------------------------
Currently, every single platform that can boot Linux gets assigned a unique
ID, known as a *machine ID*.  This ID tells the kernel which kind of
machine it's running on, and allows it to enable or disable certain
features based on what it knows about that particular machine.

Armed with the knowledge that this processor is supported, we can go about
registering a new machine ID.  This can be done on the [ARM Linux
Registry](http://www.arm.linux.org.uk/developer/machines/) page, and is a
very quick and painless process.  It will assign a machine ID you can use to
boot your board.  You will need to fill in the "directory suffix" using the
processor suffix you found using grep.  I added an entry for Falconwing, and
ended up with the Kconfig macro of CONFIG_MACH_FALCONWING and the a Type ID
of 4299.

Grab the new mach-types file, and override arch/arm/tools/mach-types, which
will allow us to use our new machine type:

    wget -O arch/arm/tools/mach-types http://www.arm.linux.org.uk/developer/machines/download.php

Adding our machine to the kernel
--------------------------------
Now that we have a machine ID, we need to modify the kernel to take
advantage of it.  We'll need to create a new machine definition file to
describe our board's peripherals, update the makefile to compile our new
definition file, and update the Kconfig file to allow us to enable support
for our board.  Because our platform is based on the i.MX233, we might as
well copy the MX23EVK machine file, because it probably contains a baseline
of features that our device should support.

Copy the MX23EVK's machine definition file:

    cp arch/arm/mach-mxs/mach-mx23evk.c arch/arm/mach-mxs/mach-falconwing.c
    
Open our copied file and modify the MACHINE_START macro to reflect the new
name.  This is the *machine type* entry from the Arm Linux Registry,
followed by a string that describes the machine.

    MACHINE_START(FALCONWING, "Falconwing Board")

Later on we'll modify this file to change various function names around, and
reflect the actual peripherals present on our board, but for now this
should be good enough to get things running.

Edit arch/arm/mach-mxs/Makefile and duplicate the entry for mach-mx23evk.o.
For my platform, I added the following line to the Makefile:

    obj-$(CONFIG_MACH_FALCONWING) += mach-falconwing.o


Edit arch/arm/mach-mxs/Kconfig, duplicate the entire MACH_MX23EVK entry,
and change its name to match the Kconfig macro handed out by the ARM Linux
Registry.  On my platform, I changed the name to read "config
MACH_FALCONWING".

Configuring the compiler
------------------------
I'm going to assume you have an ARM toolchain installed.  I'm using one that
was generated in OpenEmbedded, using the "bitbake meta-toolchain" command.
There are several toolchains available, particularly from vendors such as
[CodeSourcery
Lite](http://www.mentor.com/embedded-software/sourcery-tools/sourcery-codebench/editions/lite-edition/).
Really, just about any Linux toolchain will work so long as it's GCC and is
of a reasonably recent vintage.

Cross-compiling the kernel is actually really easy.  First, obtain the base
name for your compiler.  For example, my compiler is called
"arm-angstrom-linux-gnueabi-gcc", so the base name would be
"arm-angstrom-linux-gnueabi-".  Then, set the following environment
variables:

    export CROSS_COMPILE=[basename]
    export ARCH=arm

So on my system, I have the following set:

    export CROSS_COMPILE=arm-angstrom-linux-gnueabi-
    export ARCH=arm

Configuring the kernel
----------------------
Now that the machine definition is set, we can start configuring
Linux to match our board.  Frequently, a default configuration file is
available that will at least partially configure your board.  These are
located under the "arch/arm/configs" directory.  By searching for "mxs", I
was able to discover that there is a default config file for my processor
called "mxs_defconfig".  Given that, go to the root of the Linux directory
and run:

    make mxs_defconfig

Since the default config doesn't know about our board, we'll have to go in
and enable it.  Enter the kernel menu-based configuration by running:

    make menuconfig
    
Go to the "System Type" menu.  You should see your new platform on the
list.  Enable it, then exit the config system and save your changes.

Compiling the kernel
--------------------
By this point, we have the basics in place.  We have a basic machine set
up, we have a new machine ID, and we have a configured kernel.  We're
pretty sure the kernel won't actually do anything once booted, but hey, we
need to get it to compile before it'll run, right?

Since the environment variables for ARCH and CROSS_COMPILE are already set,
I can just type "make".  However, I want to speed up compilation on this
8-core machine, so I run the following to build everything nice and
quickly:

    make -j16

We now have a bootable kernal image, stored in arch/arm/boot/zImage.  It
probably doesn't actually do anything interesting, and in fact might not
work at all.  In the next post we'll work on the bootloader, and soon be
able to load and execute this zImage.
