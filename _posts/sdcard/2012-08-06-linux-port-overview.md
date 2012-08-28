---
layout: post
category : sdcard
tags : [falconwing, overview]
---

Bringing up Linux on a new board isn't nearly as difficult as it used to be.
In the past, you'd have to port code for the IRQ handler, memory manager,
core bringup, and individual drivers.  If you were lucky, you had a
bootloader present that made this all easy.  Otherwise you'd find yourself
spending a lot of time getting equated with the NAND flash burner.
Sometimes your processor's vendor had an in-house port that you could use as
a starting point, but oftentimes it was significantly diverged from
linux-trunk, and was several versions behind besides.

Today, things have improved significantly.  With Android, many vendors
are pushing code upstream, giving the Linux kernel very widespread support
for various boards.  When vendors don't support their parts, community
members push patches.  Many SoC chips use standard blocks for their
peripherals, so a common set of drivers can be used.  All this makes the
life of a platform builder much easier.
Furthermore, multiple platform-builder environments are available -- from the
high-end Cadillac of Ubuntu ARM down to Buildroot uClibc, no longer are system
designers forced to roll their own distros.

In short, everything is easier now than it used to be.  Perhaps it's less
glamorous, but it means that it's easier to do something cool.

Hardware
--------

In this series of posts, I'll be re-porting Linux to a piece of hardware I
happen to have lying around: the [chumby hacker
board](http://adafruit.com/products/278).  It sports an all-in-one i.MX233,
which really is an amazing little chip.  If you wanted to get one for
yourself, a 128-pin LQFP would set you back [about seven
bucks](http://www.futureelectronics.com/en/technologies/semiconductors/microprocessors/embedded-processors/Pages/3175841-MCIMX233DAG4B.aspx?IM=0),
and they couldn't be easier to work with.

This particular processor has been used in a number of consumer devices,
ranging from the [Sandisk
Fuze+](http://www.sandisk.com/products/music-video-players/fuze-plus/) MP3
player to the [I'm Watch](http://www.imwatch.it/) wristwatch.  It's
available as a hobbyist platform in the form of the Chumby Hacker Board and
the [OlinuXino](https://www.olimex.com/dev/imx233-olinuxino-maxi.html)
platform.  It's well-documented and well-understood, and isn't hard to find
hardware at all.

To get the processor running, you hook up a crystal, 3.3V, and a serial
header, and you're off.  You probably should also hook up an SD card to
give it something to boot off of.  But that's it.  Routing the DDR can be a
bit tricky, in case you want to use anything beyond the on-chip 32 kB of
RAM, but it's definitely doable.  And you know the chip will be in
production for another ten years at least, because that's how Freescale
rolls.

You can even hook up a speaker to the CPU and get 16-bit audio out, or
connect a composite video cable directly to one of the pins to get
TV-output.

Software
--------

When you first turn it on, the Chumby Hacker Board loads a secondary
bootloader, then loads an ancient version of the Linux kernel -- 2.6.28 --
and then boots to a custom distribution.  The distribution has no package
manager to speak of, and comes with only basic utilities.  It runs
[Busybox](http://www.busybox.net/) for all major commands, except linuxrc
which it replaces with its own that mounts various filesystems.

To its credit, almost every USB device imaginable is supported by a kernel
module, though the userspace support might not be there.  So for example,
while a USB video camera might be recognized if it's connected, there's no
software present to actually communicate with the camera using
[V4L2](http://en.wikipedia.org/wiki/Video4Linux).  Most input devices are
supported, though, which is nice.

Goals
-----

By the end, I'll have the following:

* A modern kernel booting
* A brand-new userspace
* Support for the serial port, USB, and SD card

I won't be using any specialized hardware, aside from a USB <-> TTL Serial
cable adapted for use with this particular pinout.  Early board bringup
frequently entails the use of JTAG or SWD cables, oscilloscopes, or even
volt meters.  I'm going to use none of that.  The entirety of my toolbox
will be:

* A chumby hacker board
* PSP power supply for the hacker board
* A USB MicroSD adapter that came bundled with a MicroSD card
* A PL2303 USB <-> TTL serial cable
* A remote Linux build system with:
  * OpenEmbedded
  * A toolchain built from OE using *bitbake meta-toolchain*
* A Macbook that I use as a terminal, and for burning images
* The [i.MX233 Processor Reference
Manual](http://www.freescale.com/files/dsp/doc/ref_manual/IMX23RM.pdf), which
Freescale

First up: Getting custom code to boot.  Let's go!
