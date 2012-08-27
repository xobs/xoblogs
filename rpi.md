---
layout: post
title: Raspberry Pi Projects
date: 2012-08-27
---

The Raspberry Pi board has taken the hobbyist market by storm.  At $25 for
the entry model and $35 for the deluxe, it's severely tempting to use it in
embedded projects.  Its 700 MHz ARM11 core is great for most embedded
projects, and its HDMI-out is just gravy.

Many Makers are interested in its 26-pin expansion header, which contains
up to 17 GPIOs, a serial port, an SPI port, a PWM port, and I2C.  All of
this goodness means it's possible to add lots of expansion boards, with the
only real requirement being that they support 3.3V -- a bit of a departure
from  the 5V Arduinos.

The other major, obvious difference between an Arduino and the Raspberry Pi
is the existence of a full Linux operating system.  It runs with the MMU
enabled, which means you get all the trimmings such as memory protection,
enforceable user levels, and task switching.  Coming from the computer
world that's not so surprising, but it's downright luxurious when coming up
from an Arduino.  Or possibly it's a pain, because now you have to deal
with APIs rather than twiddling the registers directly.  To each their own.

[Ada Fruit Industries](http://adafruit.com/) has graciously sent me a unit,
and has asked me to create drivers for a variety of peripherals.  These
pages document my experience developing low-level firmware for the
Raspberry Pi.  I'll be consulting the [Raspberry Pi
Schematic](http://www.raspberrypi.org/wp-content/uploads/2012/04/Raspberry-Pi-Schematics-R1.0.pdf),
the trimmed-down [reference
manual](http://www.raspberrypi.org/wp-content/uploads/2012/02/BCM2835-ARM-Peripherals.pdf),
and any usual Linux documentation.
