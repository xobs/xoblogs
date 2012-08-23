---
layout: post
category : firmware
tags : [falconwing, booting]
---
The bootloader has the potential to either be very easy to work with, or a
nightmare.  If you're very lucky, there's a copy of U-Boot running that has
access to your filesystem.  If you're very unlucky, there's no way to access
the bootloader at all.

Booting on the i.MX233
----------------------
Much of the boot process on the i.MX233 is documented very well in several
places, including the publicly-available reference manual.  With the values
of the fuses and boot pins on the hacker board, the boot process goes
something like:

* Load MBR off of the SD card
* Find a partition of type 0x53
* Load a command sequence off of the partition beginning at offset 0x400
* Begin executing the command sequence

On the stock Falconwing ROM, a custom bootloader is used to load the kernel
from a specific area of the disk.  While we could take advantage of this,
the fact that we have a new machine ID means that we'd need to modify it in
order to allow it to boot our kernel.  We'd also need to modify the
bootloader in order to change the kernel boot arguments.  Besides, the boot
ROM contains enough code to boot and load our Linux kernel, so why not take
advantage of that instead of using a chained loader?

Constructing the command sequence
---------------------------------
Freescale distributes a program that can turn a series of ELF files into a
command sequence file, suitable for writing directly to an SD card.  Or if
you'd rather, the command sequence has been reverse-engineered and
documented by the Rockbox group and works as a drop-in replacement, at
least for our purposes.

For elftosb, I decided I'd rather use the Rockbox reimplementation of
elftosb, because it was slightly easier to find.  Clone the Rockbox git repo
from git://git.rockbox.org/rockbox, go into rockbox/utils/imxtools/sbtools/,
type *make* and copy the resulting elftosb file to /usr/local/bin or put it
in your PATH.

The *elftosb* program requires a bitstream definition file, but you can
just get one of those from Freescale.

Pre-boot initialization
-----------------------
Before the Linux kernel can be loaded into RAM, we first have to set up
RAM.  When the processor first loads, the only memory that's available to
the system is the 32 kB of on-chip SRAM.  Timings and calibration needs to
be done at every boot for any DDR memory attached to the chip.
Additionally, various power management flags need to be set, in case the
system is booting from a battery.

Freescale provides open-source "bootlets" that can be used to do all of the
above.  As a bonus, Freescale provides a bootlet that's capable of
initializing the registers and memory required to boot Linux, which means
we have even less work to do ourselves.

Because I couldn't find them directly from Freescale, I pulled a copy of
the bootlets from
[Timesys](http://repository.timesys.com/buildsources/i/imx-bootlets/imx-bootlets-10.12.01/).
They're referenced in quite a few OpenEmbedded recipes, so you should be
able to get them from anywhere.

Go to the uncompressed imx-bootlets source code and edit the command-line
file linux_prep/cmdlines/stmp378x_dev.txt to change the console line so
that it's "console=ttyAMA0,115200" -- this is the standard ARM debug console
name.  Modify linux_prep/include/mx23/platform.h and change MACHINE_ID to
match the assigned ID from the Linux ARM Machine Registry.  Copy
arch/arm/boot/zImage from the Linux kernel tree into the imx-bootlets
directory and compile everything by running "make".  It will fail when it
goes to run "elftosb", but that's okay.

Packaging everything up
-----------------------
Now that the bootlets are compiled, the kernel is in place, and elftosb has
been build and put in the PATH, package up a bitstream file by running:

    elftosb -c linux.bd -o imx23_linux.sb

Note that the command lacks the "-z" argument.  On EVK boards and most other
i.MX 23 devices, boot encryption is enabled.  Straight from the factory, the
key is all zeroes, so in order to get the file to boot you need to encrypt
the image with a key of all zeroes.  The "-z" argument does that.  The
Falconwing boards have encryption turned off, so that argument needs to be
omitted entirely, and the bitstream file needs to be unencrypted.

One final step before the image is prepared: The SB image has to be padded
with 2048 bytes before it.  That's just how the ROM is.  So use "dd" to
create the actual boot image:

    dd if=imx23_linux.sb of=boot.bin seek=4

Writing the image to an SD card
-------------------------------
The file "boot.bin" is now usable as a boot image, and can be stored on an
SD card for booting.  Recall that the way the boot ROM finds the image is by looking on the SD card for a DOS partition table, and then locating the first
partition of type 0x53.  Since the SD card bundled with the Hacker Board
has this partition table already set up, we can just write boot.bin out to
it and be done:

How you write files depends on your platform, but a general rule of thumb
is to insert the MicroSD card into a USB reader and see what device nodes
appear.  On Linux this is generally under /dev/sda, and under OS X it's
generally /dev/disk2.  The CPM-style device strings tend to be very long
under Windows, and I'm not really sure how they work, but there are
versions of dd available to do this.

Make sure you write the file out to partition 1, and not to the whole disk.
That is, make sure that you write to e.g. /dev/sda1 and not /dev/sda.  On
my system I generally run:

    dd if=boot.bin of=/dev/disk4s1

Hello, world
------------
Put the card into the Falconwing board, attach a serial cable, apply power,
and if you're very lucky the following text will appear:

    PowerPrep start initialize power...
    Battery Voltage = 0.20V
    No battery or bad battery detected!!!.Disabling battery voltage
    measurements.
    Aug  8 201215:35:38
    EMI_CTRL 0x1C084040
    FRAC 0x92926192
    init_ddr_mt46v32m16_133Mhz
    power 0x00820710
    Frac 0x92926192
    start change cpu freq
    hbus 0x00000003
    cpu 0x00010001
    Uncompressing Linux... done, booting the kernel.

The system boots, attempts to load the kernel, but fails.  On the plus side,
the bootloader and bootlets are working, which is huge.

If you get a hex code instead of boot text, consult
[IMX23_ROM_Error_Codes.pdf](http://forums.freescale.com/freescale/attachments/freescale/IMXCOMM/165/1/IMX23_ROM_Error_Codes.pdf).  It will tell you
what the code means, but you're on your own when it comes to figuring out
what's causing the error, and how to resolve it.  Frequent errors include
accidentally encrypting the image (passing -z to eltfosb) and checksum
errors (which can result from a truncated image.)

Next time we'll dig into the kernel guts, and try to figure out why it's not
booting.
