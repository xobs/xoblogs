---
layout: post
category : sdcard
tags : [falconwing, linux, kernel, booting]
---

Since we're still trying to get the kernel up and running, we're going to
stick with the default Falconwing root filesystem for now.  Later on we'll
switch to using one built from Open Embedded, but we really only want to
change one thing at a time.  We are able to talk to the bootloader, and we
know that the kernel is getting loaded, but after it says "Uncompressing
Linux...  done, booting the kernel.", nothing appears on the screen.  This
is rather unfortunate.  We should fix that.

Speculation
-----------
Probably what's happening is it's getting stuck turning on the MMU, or the
DRAM timings are off, or something else catastrophic.  Or it could be that
it doesn't recognize our machine ID.  I've seen failures such as these in
the past.  Fortunately there's an earlyprintk feature that should allow us
to see what's going on.  Unfortunately, even though I've turned on on
"earlyprintk" under "Kernel Low-Level Deubbing" and added "earlyprintk" to
the command line, turning it on doesn't seem to do anything.

What we want to do is come up with a way to notify us when various parts of
the kernel are executed.  Common options here are to turn an LED on at
various points during the boot sequence, or to wiggle a PWM at various
frequencies.  Anything to figure out where it is.  If we were rich, we'd
use JTAG, but we're not.

What we can't do
----------------
Unfortunately we've got something else to contend with: The MMU.

When we're running in the bootloader, memory is physically mapped.  That is,
when we cast the number 0x80020000 to a "long \*", we can expect that we're
actually going to get memory at address 0x80020000.  Because peripherals
such as GPIO and serial are located at physical addresses, we can toggle
LEDs all we want by casting numbers to pointers and writing to them.

Unfortunately, sometime during the kernel startup, it enables the MMU and
switches to virtual addressing, which means that a pointer no longer
corresponds to an actual address, but instead gets translated first.  That
means that, for example, the real address 0x8002000 might really be located
at virtual address 0xc2020000.

Don't try this at home
----------------------
As a workaround, I've come up with a hamfisted idea that does the job, but
isn't elegant by any stretch of the imagination.  I know that earlyprintk
adds a function called "printch()" that simply emits a single character on the
debug port.  This call is actually smart enough to remap its raw memory
reads depending on whether the MMU is enabled or not.  So in order to see
what should be getting printed, I replace the printk definition in
kernel/printk.c with the following horrendous hack:

    asmlinkage int printk(const char *fmt, ...)
    {
    
            char buf[512];
            char *ptr = buf;
            int n, r;
            va_list ap;
            extern void printch(int);
    
            va_start(ap, fmt);
            n = vscnprintf(buf, sizeof(buf), fmt, ap);
            r = n;
            while (n>0) {
                    printch(*ptr++);
                    n--;
            }
            va_end(ap);
            return r;
    }

Kids, don't try this at home.  Or at least, never check in code that looks
like this.

Once I do that, I discover that it's actually hanging while looking for boot
media:

    VFS: Cannot open root device "(null)" or unknown-block(0,0): error -6

Furthermore, it looks like our command line is getting ignored, which
certainly would explain why earlyprintk isn't working:

    5Kernel command line: console=ttyAM0,115200

So now that we have a thread to pull on, let's see if we can't get it
booting right.

Fixing the kernel args
----------------------
I don't really want to have to go digging in the bootloader source code
every time I want to change the command line.  Besides, after further
digging it appears as though the "command lines" directory is a red-herring.
Instead, I'm going to ship a built-in command line compiled into the kernel
itself.
Run *make menuconfig* and go to Boot options.  Set the kernel to always use
the default command line, and paste the kernel command line we actually
want.  Take it out of the linux-prep directory, and be sure to change the
"console=" arguments.

Recompile the kernel, rebuild the bitstream, and re-burn it onto the SD
card.  Power it up, and behold as the kernel actually boots!
Userland is incredibly confused by the change in name for the
debug port, and our hacked-up printk is printing out confusing text, but it
all looks like it's there.

Now remove our modified printk, disable earlyprintk, rebuild, remake the sb
file, load it onto the SD card, and reboot.  Watch in awe as it actually
starts up.  Cheer.

Before we get too much further, it'll be nice to be able to actually use the
console.  Plug the SD card into a Linux machine, and mount /dev/sdb2.  Edit
/etc/inittab, and change the console so that it appears on our serial port,
rather than on a port that doesn't exist, by changing the line that says
"ttyAM0:..." to read "ttyAMA0:...".  Save, unmount, and boot the hacker
board.  Console should now work.

Thinking about USB
------------------
It is at this point that I notice that USB isn't working.  It would be
really nice to have working, because then I'll be able to load new kernels
via wifi and use kexec to launch them, rather than reloading the ROM on the
SD Card.  This shortens development time because if we botch things up and
the kernel panics at startup, we simply have to reboot and use the kernel
located on the SD card, rather than having to reburn the card.

After more  grepping through drivers/usb/ I discover that there is an OTG
Host driver present for the i.MX23, so I go about creating a platform
device for it.  Add this to mach-falconwing.c:

    static struct resource falconwing_usb_host_resources[] = {
            [0] = {
                    .start  = MX23_USBCTRL_BASE_ADDR,
                    .end    = MX23_USBCTRL_BASE_ADDR+0x1fff,
                    .flags  = IORESOURCE_MEM,
            },
    };
    
    static struct platform_device falconwing_usb_host_device = {
        .name           = "mxs_phy",
        .id         = -1,
        .resource   = &falconwing_usb_host_resources,
        .num_resources  = ARRAY_SIZE(falconwing_usb_host_resources),
    };

In the machine init, add a call to actually add the USB device:

    platform_device_register(&falconwing_usb_host_device);

After rebuilding everything and getting it running, I notice an error about
a missing clock.  In digging into that error (read: Adding lots of
printk()s all around the kernel,) I see that most other i.MX23
implementations assign resources such as USB using the device tree
mechanism, and start to rethink my approach to using a new machine
definition.

Given that, I'm going to pause here and reconsider my previous aversion to
using device tree.  Next time I'm going to investigate what it would take to
implement device tree on this board, and explore the wonderful new world of
descriptor-based device configuration.
