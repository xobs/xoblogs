---
layout: post
category : rpi
tags : [hardware]
---

Pulse Width Modulators are useful tools in the Maker toolbox.  They produce
a signal that is great for driving servo motors, making square-wave tones,
and acting as a very simple clock source.  The Raspberry Pi has a single
PWM port, but in order to drive it you need to directly map register memory
and talk directly to several different subsystems, each of which has a
confusing chapter in the manual (if it's documented at all) in order to get
it to work.  Let's see if we can create a kernel driver to make life
simpler.  This post will detail all of the housekeeping that goes into
creating a basic driver.  We'll get into actual PWM stuff later on.

Background
----------
Many devices live under /dev/.  This is great for stream-oriented devices
such as sound cards, printers, and serial ports, but doesn't really make
sense for persistent devices such as LEDs, batteries, and PWMs.  Linux has
a different faculty that more completely describes devices attached to the
system (and their current attributes) located in sysfs under /sys/.

A device indicates which bus it's attached to, and then indicates
it's of a particular class.  Thus, a webcam plugged in via USB would be in
the bus "usb", and would be a member of the class "v4l2" (Video for Linux).
LEDs generally are platform devices, and so show up under the bus
"platform".

Since sysfs is a filesystem, it allows for an arbitrary number of files to
be connected to a given device.  LEDs allow for users to select LED
brightness and state simply by writing values into various files.  USB
provides useful information such as device and vendor IDs by adding extra
files in sysfs.  We'd like to be able to specify various attributes of a
PWM, such as frequency and duty cycle, and report back things like the
device's actual operating frequency if the various multipliers can't lock
on to the requested one.

Setting up the Module
---------------------
Newer kernels have a "PWM" device type, but the kernel I'm working with
doesn't yet have one.  Therefore I'm going to take pains to avoid colliding
with any namespaces that might appear in the future.  I'll be placing my
module in the "misc" device category, because it doesn't seem to fall under
any other category.  Note that I will be creating an actual "struct
device", as opposed to a "struct miscdevice".  We don't actually need a
/dev/ entry, so we should be fine.

To create a config entry for the new device, edit drivers/misc/Kconfig and
add a config option to enable the module.  Just duplicate a previous entry
and make it look reasonably similar.  The important line to change is
"config XXXXXXX", which will define your configuration entry.  I named mine
"config RPI_PWM".  Make your new entry depend on MACH_BCM2708 so it'll only
show up when the Raspberry Pi machine is enabled.

Now that we have a config entry, we need to tell the kernel to compile our
module.  Edit drivers/misc/Makefile and add a line to compile our module if
its corresponding config option is enabled.  Since I'm calling the file
rpi-pwm.c, I'll add the following line to the bottom of
drivers/misc/Makefile:

    obj-$(CONFIG_RPI_PWM)          += rpi-pwm.o

Now, enable RPI_PWM by running "make menuconfig" and going to Drivers ->
Misc and selecting RPI_PWM.  For ease of development, make sure to build it
as a module to avoid needing to recompile a kernel from scratch every time.

As a final bit of initial housekeeping, write the very basics of the
module.  The following code is all you need to get a basic kernel module
going.  The function rpm_pwm_init will be run when the module is loaded,
and the function rpm_pwm_cleanup will be run when it's removed:

    #include <linux/module.h>
    int __init rpi_pwm_init(void) {
        printk(KERN_DEBUG "Hello, world!\n");
        return 0;
    }

    void __exit rpi_pwm_cleanup(void) {
        printk(KERN_DEBUG "Goodbye, world!\n");
    }
    module_init(rpi_pwm_init);
    module_exit(rpi_pwm_cleanup);
    MODULE_LICENSE("GPL");

Compile your kernel, then copy drivers/misc/rpi-pwm.ko to your device.
Load it with "insmod rpi-pwm.ko", check for your printk output by running
"dmesg | tail", and you should be all ready to go.

Defining a Device Class
-----------------------
We want to create a new device class under which to put our PWM device.
The logical class would be "pwm", but since we want to avoid collisions
with potential future PWM drivers, let's add a vendor tag.  We'll call our
class "rpi-pwm".  Actually define the class by adding this to your source
file:

    static struct class pwm_class = {
        .name =         "pwm-class",
        .owner =        THIS_MODULE,
    };

Register the class in your rpi_pwm_init() function:

    class_register(&pwm_class);

You will, of course, want to check the return value from that function.
Make sure it's 0, and print an error if not.  Put a corresponding
deregistration call in your cleanup function:

    class_unregister(&pwm_class);

Creating the Device
-------------------
Actually creating the device and registering it with the system isn't very
difficult either.  We want it to be a member of our special PWM class, and
under the "platform" bus.  We want it to be pwm0, and we want to pass some
pointer to data for any functions that have to deal with the device.  Create
a global "static struct device *rpi_pwm_dev", and add a call in your init
that looks like this, sometime after the class_register() call:

    rpi_pwm_dev = device_create(&pwm_class, &platform_bus,
                            MKDEV(0, 0), &pwms[pwm], "pwm%u", 0);

If the device cretion fails (e.g. if rpi_pwm_dev is NULL), something is
wrong.  Bail, unregister the class, and return an error such as -ENODEV.
Don't forget to add the corresponding device destruction call to your
cleanup routine:

    device_unregister(rpi_pwm_dev);

Creating sysfs Entries
----------------------
This is where things get a little interesting.  Add the following code to
the top of your source file, just to get a very basic sysfs entry working:
    static ssize_t hello_show(struct device *d,
                    struct device_attribute *attr, char *buf) {
        return sprintf(buf, "Hello there!\n");
    }
    static ssize_t hello_store(struct device *d,
                 struct device_attribute *attr, const char *buf, size_t count) {
        printk(KERN_DEBUG "Got data: %s\n", buf);
        return count;
    }
    static DEVICE_ATTR(hello, 0666, hello_show, hello_store);
    static struct attribute *rpi_pwm_sysfs_entries[] = {
        &dev_attr_hello.attr,
        NULL
    };
    static struct attribute_group rpi_pwm_attribute_group = {
        .attrs = rpi_pwm_sysfs_entries,
    };

Now you have an array of attribute groups.  In your init, add them to the
device you've just created.  Add this below the device_create() call:

    sysfs_create_group(&rpi_pwm_dev->kobj, &rpi_pwm_attribute_group);

Add the corresponding removal call to the cleanup:

    sysfs_remove_group(&rpi_pwm_dev->kobj, &rpi_pwm_attribute_group);

Compile and load your module.  If all goes well, you should be able to
change to /sys/class/rpi-pwm/pwm0 and see the usual sysfs entries, plus a
new one called "hello".  If you cat this file, it should report to you the
message "Hello there!".  If you write data into this file, it should appear
under "dmesg | tail".  This will be our basis for talking to the kernel.

What's Next
-----------
We haven't done anything here that's specific to the PWM.  In fact, we've
just created a generic module that doesn't really do anything interesting
except exist.  If you've checked all errors and clean up everything
correctly, you should be able to load and unload your driver all day
without leaking any data.

Next we'll get around to actually making the PWM do something.
