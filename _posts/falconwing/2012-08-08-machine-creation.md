---
layout: post
category : firmware
tags : [falconwing, linux, kernel, booting]
---

There are a large-but-finite number of processors supported by the Linux
kernel.  Many different processor families and memory controllers make up
the 700-megabyte download.  Frequently when bringing up Linux on an
unfamiliar piece of hardware, much of the code required to boot the device
already exists, it just needs to be tied together.  This series will follow
me trying to port the Linux "master" branch to the chumby Hacker Board,
which runs a Freescale i.MX233 SoC and is known as "Falconwing".

Note that I'll be using a hardcoded machine type here, rather than going for
a device tree implementation.  This is because I don't want to modify the
bootloader to support passing device tree data.  I could statically link a
platform-specific device tree, but that would be no different from using the
old mach-based approach, so there doesn't seem to be an advantage there.

I'm going to assume you have an ARM toolchain installed.  I'm using one that
was generated in OpenEmbedded.  It's available [for
download](http://kosagi.progress.sg/kovan/angstrom-eglibc-x86_64-armv5te-v2012.07-core-toolchain.tar.bz2)
for 64-bit Intel Linux, but any toolchain will work.  I have the
environment variable "ARCH" set to "arm", and "CROSS_COMPILE" set to
"arm-angstrom-linux-gnueabi-" which makes kernel compilation easier:

    export CROSS_COMPILE=arm-angstrom-linux-gnueabi-
    export ARCH=arm

The very first step in bringing up Linux on a new ARM platform is to make
sure your processor is supported.  If not, you've got a much harder road
ahead of you.  If it is supported, even partially, then you can avoid much
of the pain that comes with bringing up a new memory controller.  I found my
processor -- the i.MX233 -- goes by the name "mxs" in Linux:

    smc@build-ssd:~/linux/arch/arm$ grep -ir mx23 mach-* | cut -d/ -f1 | uniq -c
          6 mach-mmp
        678 mach-mxs
    smc@build-ssd:~/linux/arch/arm$ 

Armed with the knowledge that this processor is supported, we can go about
registering a new platform.  This can be done on the [ARM Linux
Registry](http://www.arm.linux.org.uk/developer/machines/) page, and is a
very quick and painless process.  It will assign a machine ID you can use to
boot your board.  You will need to fill in the "directory suffix" using the
processor suffix you found using grep.  I added an entry for Falconwing, and
ended up with the Kconfig macro of CONFIG_MACH_FALCONWING and the a Type ID
of 4299.

Grab the new mach-types file, and override arch/arm/tools/mach-types, which
will allow us to use our new machine type:

    wget -O arch/arm/tools/mach-types http://www.arm.linux.org.uk/developer/machines/download.php

Now to add our machine to the kernel.  Edit arch/arm/mach-mxs/Kconfig,
duplicate the MACH_MX23EVK entry, and change its name to match your Kconfig
macro.  I copied the entire entry and changed the config line to read
"config MACH_FALCONWING", and changed the text entry to match.  Since my
board doesn't have a framebuffer I removed MXS_HAVE_PLATFORM_MXSFB.

Since we're changing the machine name, we also have to come up with a new
board definition file.  This file includes a MACHINE_START macro, and is the
initial point where machine-specific code is run, including adding various
machine-specific devices.  Edit Makefile and notice the following line:

    obj-$(CONFIG_MACH_MX23EVK) += mach-mx23evk.o

A good guess would be that the MX23 EVK board we're copying has its
MACHINE_START defined in the file mach-mx23evk.c.  I want to copy this
wholesale, so I added the following line beneath it:

    obj-$(CONFIG_MACH_FALCONWING) += mach-falconwing.o

Now, create the machine definition file by duplicating an existing one.  I
just copied mach-mx23evk.c to mach-falconwing.c.  Open it and modify the
MACHINE_START line to reclect the new name.  In my case I changed it to
read:

    MACHINE_START(FALCONWING, "Falconwing Board")

Later on we'll modify this file to change various function names around, and
reflect the actual devices present on our board, but for now this should be
good enough to get things running.

Armed with a platform ID and a kernel config entry, we can start configuring
Linux to match our board.  Frequently, a default configuration file is
available that will at least partially configure your board.  These are
located under the "arch/arm/configs" directory.  By searching for "mxs", I
was able to discover that there was a default config file for my processor
called "mxs_defconfig".  Given that, go to the root of the Linux directory
and run:

    make mxs_defconfig

Since the default config doesn't know about our board, we'll have to go in
and enable it.  Go to the "System Type" menu.  You
should see your platform on the list.  Enable it, then exit the kernel menu
config and save your changes.  Do a test compile to verify everything works.
Since the environment variables for ARCH and CROSS_COMPILE are already set,
I can just type "make".  However, I want to speed up compilation on this
8-core machine, so I run the following to build everything nice and
quickly:

    make -j16

We now have a bootable kernal image, stored in arch/arm/boot/zImage.  It
probably doesn't actually do anything interesting, and in fact might not
work at all.  In the next post we'll work on the bootloader, and soon be
able to load and execute this zImage.
