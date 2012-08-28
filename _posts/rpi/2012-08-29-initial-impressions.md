---
layout: post
category : rpi
tags : [hardware]
---

Getting new hardware is always an exciting time.  When dealing with old,
familiar hardware, it's possible to become complacent.  When a problem
arises, the solution is simple, obvious, and requires no intake of new
knowledge.  At worst you know exactly where to find the answer in the
manual or source file.  But new hardware is great.  It's a trip back to
ignorance.

So it is with the Raspberry Pi.  I've never worked with Broadcomm hardware
before, so I'm wholly unfamiliar with their terminology, layout, labeling,
and architecture.  I'm familiar enough with ARM parts that I can have a
toehold there, but everything else is foreign.

Configuring the Kernel
----------------------
I'm using the [kernel from
Adafruit](https://github.com/adafruit/adafruit-raspberrypi-linux).  A
simple clone, or a fork/clone, should suffice.  As usual, doing a pull of
the kernel can take a very long time.

As of this writing, a default .config file can be found in the file
adafruit.config.  Activate it by running:

    cp adafruit.config .config

You can then configure various kernel settings by running:

    make ARCH=arm menuconfig

Setting up a Toolchain
----------------------
In the past I've used ARM9 cores that use the ARMv5 instruction set.  So
the toolchain I usually use only supports instructions that are present in
ARMv5.  If I try to compile the Linux kernel that uses ARMv6 instructions,
it just doesn't fly:

      CC      drivers/usb/host/dwc_common_port/dwc_common_linux.o
    /tmp/cct1arxL.s: Assembler messages:
    /tmp/cct1arxL.s:738: Error: selected processor does not support `cpsid i'
    /tmp/cct1arxL.s:1008: Error: selected processor does not support `cpsid i'
    make[4]: *** [drivers/usb/host/dwc_common_port/dwc_common_linux.o] Error 1
    make[3]: *** [drivers/usb/host/dwc_common_port] Error 2
    make[2]: *** [drivers/usb/host] Error 2
    make[1]: *** [drivers/usb] Error 2
    make: *** [drivers] Error 2

So I try for a different toolchain.  A processor I'm trying out supports
ARMv7a, so I try switching to that.  It compiles, but as long as I have the
USB module enabled, it won't boot.  Linux dumps me into kgdb with the
following backtrace:

    ...
    Entering kdb (current=0xcb846c80, pid 1) Oops: (null)
    due to oops @ 0xc05bf468
    
    Pid: 1, comm:              swapper
    CPU: 0    Not tainted  (3.1.9adafruit+ #6)
    PC is at dwc_otg_driver_init+0xc/0x150
    LR is at do_one_initcall+0x48/0x1a4
    pc : [<c05bf468>]    lr : [<c000861c>]    psr: 60000013
    sp : cb849f70  ip : cb849f88  fp : cb849f84
    ...
    [<c0008180>] (do_undefinstr+0x0/0x1ac) from [<c0406eec>]
    (__und_svc+0x4c/0x80)
    Exception stack(0xcb849ee8 to 0xcb849f30)
    9ee0:                   c05bf45c c05e0008 cb849f88 00000000 c05dc3ec
    c060fb60
    9f00: cb848008 00000013 00000000 00000000 cb848000 cb849f84 cb849f88
    cb849f70
    9f20: c000861c c05bf468 60000013 ffffffff
     r8:00000000 r7:cb849f1c r6:ffffffff r5:60000013 r4:c05bf46c
    [<c05bf45c>] (dwc_otg_driver_init+0x0/0x150) from [<c000861c>]
    (do_one_initcall+0x48/0x1a4)
     r4:c05dc3ec r3:00000000
    [<c00085d4>] (do_one_initcall+0x0/0x1a4) from [<c05aa870>]
    (kernel_init+0x80/0x128)
    [<c05aa7f0>] (kernel_init+0x0/0x128) from [<c000efdc>]
    (kernel_thread_exit+0x0/0x8)
     r5:c05aa7f0 r4:00000000
    
    kdb> 

Basically, it's an undefined instruction.  Thinking a bit, I realize that
it probably has something to do with the difference between ARMv6 and
ARMv7.  Firing up the debugger and disassembling the function in question
gets us this:

    smc@build-ssd:~/rpi/adafruit-raspberrypi-linux$ arm-angstrom-linux-gnueabi-gdb vmlinux
    GNU gdb (GDB) 7.4
    Copyright (C) 2012 Free Software Foundation, Inc.
    License GPLv3+: GNU GPL version 3 or later
    <http://gnu.org/licenses/gpl.html>
    This is free software: you are free to change and redistribute it.
    There is NO WARRANTY, to the extent permitted by law.  Type "show copying"
    and "show warranty" for details.
    This GDB was configured as "--host=x86_64-angstromsdk-linux
    --target=arm-angstrom-linux-gnueabi".
    For bug reporting instructions, please see:
    <http://www.gnu.org/software/gdb/bugs/>...
    Reading symbols from /home/smc/rpi/adafruit-raspberrypi-linux/vmlinux...(no
    debugging symbols found)...done.
    (gdb) disas dwc_otg_driver_init
    Dump of assembler code for function dwc_otg_driver_init:
       0xc05bf45c <+0>:	mov	r12, sp
       0xc05bf460 <+4>:	push	{r3, r4, r11, r12, lr, pc}
       0xc05bf464 <+8>:	sub	r11, r12, #4
       0xc05bf468 <+12>:	movw	r0, #42148	; 0xa4a4
       0xc05bf46c <+16>:	movw	r1, #42164	; 0xa4b4
    ...

If you look at the kgdb dump, you'll notice it says that it crashed at
dwc_otg_driver_init+0xc/0x150.  That means that the function
dwc_otg_driver_init is 0x150 bytes long, and the actual crash occurred at
offset 0xc (12).  In looking at the gdb dump, we see the "movw" instruction
is at that offset.

Sure enough, movw is an ARMv7 instruction that somehow snuck into our
kernel image.  It probably came as part of the CFLAGS environment variable,
which gets set by the toolchain.  Sure enough, this seems to be the case:

    smc@build-ssd:~/rpi/adafruit-raspberrypi-linux$ echo $CFLAGS
    -march=armv7-a -fno-tree-vectorize -mthumb-interwork -mfloat-abi=softfp
    -mfpu=neon -mtune=cortex-a9
    --sysroot=/usr/local/oecore-x86_64/sysroots/armv7a-angstrom-linux-gnueabi

After replacing -march=armv7-a with -march=armv6, and removing the -mtune
line, we finally have a kernel that can be built!


Trying Out the New Kernel
-------------------------
The Broadcom CPU reads the file /boot/kernel.img and then boots it.  To
replace the kernel, you replace that file.  Rather straightforward, but it
requires you to pull the SD card out of the Pi, plug it into a computer,
copy the file, and move it back.  That's a lot of effort, and requires
movement.

Fortunately, the kernel comes with kexec, so it's possible to load a new
kernel without rebooting.  That makes development oh so much easier.
First, ensure the kexec program is installed:

    root@raspberrypi:~# apt-get install kexec-tools
    Reading package lists... Done
    Building dependency tree       
    Reading state information... Done
    The following NEW packages will be installed:
      kexec-tools
    0 upgraded, 1 newly installed, 0 to remove and 0 not upgraded.
    Need to get 64.9 kB of archives.
    After this operation, 187 kB of additional disk space will be used.
    Get:1 http://mirrordirector.raspbian.org/raspbian/ wheezy/main kexec-tools
    armhf 1:2.0.3-1 [64.9 kB]
    Fetched 64.9 kB in 4s (14.5 kB/s)      
    Preconfiguring packages ...
    Selecting previously unselected package kexec-tools.
    (Reading database ... 56425 files and directories currently installed.)
    Unpacking kexec-tools (from .../kexec-tools_1%3a2.0.3-1_armhf.deb) ...
    Processing triggers for man-db ...
    Setting up kexec-tools (1:2.0.3-1) ...
    Generating /etc/default/kexec...
    root@raspberrypi:~# 

Now, copy the kernel image over.  I just use scp:
    smc@build-ssd:~/rpi/adafruit-raspberrypi-linux$ !scp
    scp arch/arm/boot/Image root@raspberrypi.local:
    Image                                         100% 6175KB   6.0MB/s   00:01
    smc@build-ssd:~/rpi/adafruit-raspberrypi-linux$

Then, on the Pi, load and run the new image:
    kexec -l Image ; lexec -e

And that's it.  Now we have the ability to compile a new kernel, load the
new kernel, and figure out where problems are coming from.  Next up:
Development.
