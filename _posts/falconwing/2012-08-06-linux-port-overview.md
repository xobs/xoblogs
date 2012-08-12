---
layout: post
category : firmware
tags : [falconwing, linux, kernel, booting]
---
{% include JB/setup %}

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

In this series of posts, I'll write about re-porting Linux to a new board.
I'd like to do some work with a chumby Hacker Board, but it's running an
ancient version of the kernel -- 2.6.28 -- and has a non-standard Linux
distribution.  The board's CPU is a Freescale i.MX233 (sometimes called an
i.MX23), which is supported by Linux under the machine family "MXS".
My goal for this project is to get it to run a stock Linux kernel under Open
Embedded, with only minimal patching.  I'll document the steps required to
get a new machine ID, configure a machine, compile a kernel, get it booting,
and eventually get it running a modern distro.

I'm not going to use any specialized tools such as JTAG.  The most
specialized hardware I'll have is a USB-to-TTL-serial cable, which I'll use
to look at printk messages and bootloader progress.  Along the way I'll
consult the [i.MX233 Processor Reference
Manual](http://www.freescale.com/files/dsp/doc/ref_manual/IMX23RM.pdf), which
Freescale freely provides.  I'm going to start by replacing the bootloader
and kernel, and only after that's stable will I start to work on replacing
the userspace.
