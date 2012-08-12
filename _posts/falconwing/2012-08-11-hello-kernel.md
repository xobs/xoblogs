---
layout: post
category : firmware
tags : [falconwing, linux, kernel, booting]
---
{% include JB/setup %}

Since we're still trying to get the kernel up and running, we're going to
stick with the default Falconwing root filesystem for now.  Later on we'll
switch to using one built from Open Embedded, but we really only want to
change one thing at a time.  The kernel is getting loaded, but after it says
"Uncompressing Linux...  done, booting the kernel.", nothing appears on the
screen.  This is rather unfortunate.  We should fix that.

Probably what's happening is it's getting stuck turning on the MMU, or the
DRAM timings are off, or something else catastrophic.  Or it could be that
it doesn't recognize our machine ID.  I've seen failures such as these in
the past.  Fortunately there's an earlyprintk feature that should allow us
to see what's going on.  Unfortunately, even though I've turned on on
"earlyprintk" under "Kernel Low-Level Deubbing" and added "earlyprintk" to
the command line, turning it on doesn't seem to do anything.

As a workaround, I've come up with a hamfisted idea that does the job, but
isn't elegant by any stretch of the imagination.  I know that earlyprintk
adds a function called "printch()" that simply emits a single character on the
debug port.  So in order to see what is getting printed, I replace the
printk definition with the following:

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

Once I do that, I discover that it's actually hanging while looking for boot
media:

    VFS: Cannot open root device "(null)" or unknown-block(0,0): error -6

Furthermore, it looks like our command line is getting ignored, which
certainly would explain why earlyprintk isn't working:

    5Kernel command line: console=ttyAM0,115200

So now that we have a thread to pull on, let's see if we can't get it
booting right.  Because I don't really want to have to go digging in the
bootloader source code every time I want to change the command line, and it
appears as though the "command lines" directory is a red-herring, I think
I'll modify the kernel to use a built-in command line.  Run "make
menuconfig" and go to Boot options.  Give it a default command line, and set
it to always use the default kernel command line.  While we're here, enable
kexec as well, so we'll be able to reload the kernel without building a new
sb image in the future.

After recompiling and reloading the bitstream file, we have a kernel
booting!  Userland is incredibly confused by the change in name for the
debug port, and our hacked-up printk is printing out confusing text, but it
all looks like it's there.  Now remove our modified printk, disable
earlyprintk, rebuild, remake the sb file, load it onto the SD card, and
reboot.  Watch in awe as it actually starts up.

Before we get too much further, it'll be nice to be able to actually use the
console.  Plug the SD card into a Linux machine, and mount /dev/sdb2.  Edit
/etc/inittab, and change the console so that it appears on our serial port,
rather than on a port that doesn't exist, by changing the line that says
"ttyAM0:..." to read "ttyAMA0:...".

It is at this point that I notice that USB isn't working.  It would be
really nice to have working, because then I'll be able to use kexec, rather
than reloading the ROM on the SD Card.  In digging into things, I notice
that there is an OTG Host driver present for the i.MX23, so I go about
creating a platform device for it.  Add this to mach-falconwing.c:

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

After rebuilding everything and getting it running, I notice an error about
a missing clock.  In digging into that error, I see that most other i.MX23
implementations assign resources such as USB using the device tree
mechanism, and start to rethink my approach.

Given that, I'm going to pause here and reconsider my previous aversion to
using device tree.  Next time I'm going to investigate what it would take to
implement device tree on this board, and explore the wonderful new world of
descriptor-based device configuration.
