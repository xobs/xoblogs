---
layout: post
category : rpi
tags : [hardware]
---

We have the basics of a kernel module, and the basics of PWM control in
userspace.  All that's left to do now is merge the two and add some
spit-and-polish.

Direct Memory Access in the Kernel
----------------------------------
The preferred method of accessing memory-mapped devices in the kernel is to
create a generic driver that requests a resource object from the platform,
and then memory-maps that region.  If we were to get this module merged,
that's the approach we'd take.  However, because we're creating a brand-new
class of device, it doesn't make sense to bend over backwards to make this
module portable.  So to save ourselves some trouble, we'll just hardcode
the PWM offsets in the kernel source file itself.

Since pointers in the kernel are all virtually mapped, we need a way to
convert the physical register offsets defined in our toy userspace
application to work in the kernel.  The function *ioremap()* can do this
for us without too much trouble.  We can take define lines directly from
our toy application, and then pass these definitions to ioremap(),
requesting 1024 bytes of contiguous memory:
    clk_regs = ioremap(CLOCK_BASE, 1024);
    pwm_regs = ioremap(PWM_BASE, 1024);
    gpio_regs = ioremap(GPIO_BASE, 1024);

With these statements, we obtain direct access to the various registers
available for clock, PWM, and GPIO.

To read from/write to registers, use **__raw_writel()** and
**__raw_readl()** to read/write long integers.  Note that the syntax may be
backwards from what you're used to.  Hence, to write a value out to the
PWMCLK_CNTL register, you'd write:
    __raw_writel(0x5A000011, PWMCLK_CNTL);

An Aside About Resources
------------------------
There are a number of reasons why we don't want to use resources.  They're
great for managing a shared resource, but changes involved would be far too
invasive.  There are functions to set GPIO pins to have the various
Alternate Functions, but these calls are declared static, and require us to
pass GPIO bank objects.  It seems easiest, then, to simply hard-code
requests to change pin functions.

Additionally, the entire resource system is set up to prevent conflicts.
Which is unfortunate, because we most certainly are going to cause
conflicts.  Our code uses the PWM, which is the exact same subsystem as the
audio controller.  If the kernel were to manage resources and make sure
we're not both using PWMs at the same time, it would completely defeat the
purpose of the module.  Especially since we've added the ability to send
one of the headphone channels out the PWM pin.

So instead, we'll try to tread as lightly as possible, and hope that the
user doesn't try to play music while operating the PWM module.

Module Autoloading
------------------
Having the module load automatically at boot is a neat trick, and adds a
nice level of polish to a project.  Because the Raspberry Pi uses a full
modutils implementation, we can add an alias to the kernel module we're
writing, and modify the machine's definition to note that it supports our
module.

To start, add an alias to the bottom of the kernel source file.  I've opted
to follow the naming convention of other modules on the system, but you can
put any valid identifier after the "platform:" string:
    MODULE_ALIAS("platform:bcm2708_pwm");

Then, add the device definition to the machine file.  Open up
**arch/arm/mach-bcm2708/bcm2708.c** and add a struct definition:
    static struct platform_device bcm2708_pwm_device = {
        .name = "bcm2708_pwm",
        .id = 0,
    };

Note that the "name" must match the portion after "platform:".  This is the
only key that it uses to match a module to a device.

Finally, in the MACHINE_INIT function (*bcm2708_init()* in this case), add
the device:
    bcm_register_device(&bcm2708_pwm_device);

In order to get the module to autoload, you need to copy the .ko file to
your modules directory (/lib/modules/[kernel-version]) and re-run module
dependencies:
    scp drivers/misc/rpi-pwm.ko root@raspberrypi.local:/lib/modules/3.1.9/
    ssh root@raspberrypi.local "depmod -a"

Load the newly-built kernel, and the module should autoload.

Avoid Floating-Point
--------------------
The original code used floating-point division to get an accurate clock.
Floating point is disallowed in the Linux kernel, because it's not designed
to handle exceptions, nor will it preserve floating point registers if a
context switch happens.  So stick with integers, or fixed-point at worst.

As such, it pays to do divisions last, to avoid rounding errors and
quantization effects.  For example, if you want to compute 6/5*10, you
should compute (6*10)/5 = 12, instead of (6/5)*10 = 10.  That's why in the
**rpi_pwm_set_frequency()** function call we have this:
    RNG = dev->mcf/dev->frequency;
    DAT = RNG*dev->duty/100;

But we move the division later when setting it as a servo by distributing
the division for RNG among the calls to scale the frequency from a
percentage to units of [0.5-2.5] milliseconds:
    DAT = (mcf*2*dev->servo_val/dev->servo_max/frequency/20)
        + (mcf/frequency/40);

Grinding Through Control Files
------------------------------
By this point, programming the kernel module is just a matter of
implementing each required function for each control file.  There are a few
choke points that all functions will call, such as **rpi_pwm_activate()**
and **rpi_pwm_deactivate()**, but for the most part each control function
does its own thing.

Write code, compile, test, and iterate.  Generally I come up with a
one-liner that will reload the module.  In one window I fire off the
compile command, and in the other I copy the module and install it.  So for
example, I'd build the module by running:
    make -j16

Then on the development board, I'd run:
    cd; scp user@build.local:rpi/linux/drivers/misc/rpi-pwm.ko .; \
        rmmod rpi-pwm; insmod rpi-pwm.ko; cd /sys/class/rpi-pwm/pwm0

If the board crashes, it's a simple matter to restart.  If it doesn't then
I'm already in the PWM directory and I can test the behavior of
newly-created control files.

Wrapping Things Up
------------------
That's about it in terms of kernel development.  Most of the fun stuff can
be done in userspace, where it's harder to blow up the system.  The final
module is shipping in the [Ada Fruit
kernel](https://github.com/adafruit/adafruit-raspberrypi-linux/blob/rpi-patches/drivers/misc/rpi-pwm.c).
It seems to work well, and achieves the goal of being able to easily adjust
the PWM by simply writing values out to files.
