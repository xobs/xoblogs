---
layout: post
category : firmware
tags : [novena, linux, kernel, booting]
---
{% include JB/setup %}

## About the platform

We have a brand-new platform we're bringing up.  Novena is based on the
i.MX6 processor, and is designed to be a DIY-laptop.

The SoC has a lot of features such as a beefy 3D engine, video decode, SATA
controller, and a 1x PCIe slot, among others.  The plan is to have a SO-DIMM
slot that can take up to 4 GB of RAM, an internal and an external SD slot, a
PCIe slot for wifi, a power management IC (PMIC) that can charge and monitor
cheap RC car batteries, and an LCD connector that can drive most LVDS
displays.

## Preparing for layout

Up until now, we've only had reference manuals for this part.  While they
are great for doing principal design, they're not very good for getting a
feel for some of the chip's parameters.  Things like heat dissipation,
processor performance, and chip capabilities are all only hinted at in the
manual.  Nothing beats being able to point a thermometer at an actual chip.

It also gives you a greater understanding for different sections of the
manual.  Knowing how various boot modes behave will allow you to know how to
wire up the boot peripherals, and being able to probe voltages is a great
way to crib from an existing, working design.

With that in mind, we finally got a evaluation kit (EVK) for the i.MX6.  Now
we can start probing lines and bringing up Linux.

## Plan for board bringup

I'm going to go about the board bringup somewhat backwards.  I'm going to go
in the following order:

1. Build userspace using OpenEmbedded
2. Build a kernel, either using linux-next or Freescale's kernel
3. Switch the EVK to booting from SD, and replace the EVK's SPI-NOR boot mode.

By getting userspace working, we can answer questions with regards to power
consumption, 3D performance, dual-monitor support (HDMI and LVDS), processor
speed management, and the user experience.  Since the EVK's kernel already
works, we'll just use that.

After we have a working userspace, we can start looking at which kernel we'd
like to use.  Ideally we'll use linux-next, and push patches upstream.  In
the interim, we may need to use Freescale's kernel, or Linaro's kernel.  But
for the bootloader, we'll continue to use the copy of U-Boot loaded into the
EVK's SPI NOR.

Finally, we'll flip the switch on the EVK to allow booting directly from SD,
rather than going from the SPI NOR.  This will mimic the final product,
which will allow booting either from the internal SD card, an internal SATA
drive, or an external SD card.

Next up: Building OpenEmbedded for this platform.
