---
layout: post
category : sdcard
tags : [falconwing, linux, kernel, booting]
---

I've never done anything with Device Tree before.  The closest I've come is
back when I was a PowerPC Macintosh user, and I would enter the Open
Firmware prompt for fun.  Running "dev /" and then "ls" would display a
list of all devices on the system, along with their addresses and
compatible drivers.  Device Tree is a similar mechanism for ARM, wherein a
binary structure describing hardware components is passed to the kernel at
boot, either as part of the Atags or at the end of the kernel image.

Getting started with device tree
--------------------------------
To learn about Device Tree, I'm consulting
[Documentation/devicetree/booting-without-of.txt](http://git.kernel.org/?p=linux/kernel/git/next/linux-next.git;a=blob;f=Documentation/devicetree/booting-without-of.txt;h=d4d66757354e77f055b1f927789ae0d8ab234499;hb=HEAD).
Grab the DTC compiler by cloning git://git.jdl.com/software/dtc.git,
compile it, and install it.  Make sure dtc is somewhere in your path:

    dtc --help

All of the ARM device tree source files are located in arch/arm/boot/dts/.
Copy a reference file to make our new machine file; I copied imx23-evk.dts
to create imx23-falconwing.dts.  Remove fields that aren't used, such as
"gpmi-nand" and "lcdif".  Modify the "memory" field so that it ends at
0x04000000 rather than 0x08000000, as our board has 64 megabytes of RAM and
not 128.  Leave everything else as-is for now.

There are two ways to pass the device tree structure to the kernel.  The
first is to load it at a known offset and then pass that offset to the
kernel as part of the bootloader.  The second is to just tack a compiled
device tree file onto the end of the kernel image.  For simplicity, we'll
use the second approach.  Run *make menuconfig*, go to "Boot options", and
select "Use appended device tree blob to zImage".

Compile the kernel, then compile the DTS file and append it to the zImage:

    make zImage
    dtc -o arch/arm/boot/imx23-falconwing.dtb -O dtb arch/arm/boot/dts/imx23-falconwing.dts
    cat arch/arm/boot/imx23-falconwing.dtb >> arch/arm/boot/zImage

To be sure the device tree data gets used, I modified the linux_prep code
to not pass any ATAGs.  To do this, I edited linux_prep/core/entry.S and
changed the final "mov r2, r0" in \_start to "mov r2, #0".

Cycle everything again and boot the kernel.  It starts up, but fails to
recognize the root filesystem because the SD card can't be found.
Amusingly, it's also slightly confused about the lack of ATAGs:

    [    0.150000] No ATAGs?

USB is also not quite working.  In adding debug statements, it looks like
we're back up against the inability to find the USB clock.  Well, at the
very least we're using device tree now, even if things aren't quite working
yet.  It's a tossup whether USB or SD will be harder to get working, but
since I previously had SD working using the mach-based approach, I'll go
for that.

"Fixing" MMC
------------

A quick scan through the MMC driver reveals a function called
mxs_mmc_get_cd() that appears to return whether a card is present or not.
It could be that the card-detect is broken with the current device tree
implementation.  As a test, I do a printk of the value it's returning, and
sure enough it's coming back as "0".  So in order to quickly hack SD into
working, I apply the following patch so I can get on with investigating the
USB problem:

    diff --git a/drivers/mmc/host/mxs-mmc.c b/drivers/mmc/host/mxs-mmc.c
    index a51f930..8a2cbb8 100644
    --- a/drivers/mmc/host/mxs-mmc.c
    +++ b/drivers/mmc/host/mxs-mmc.c
    @@ -187,8 +187,7 @@ static int mxs_mmc_get_cd(struct mmc_host *mmc)
     {
            struct mxs_mmc_host *host = mmc_priv(mmc);
     
    -       return !(readl(host->base + HW_SSP_STATUS(host)) &
    -                BM_SSP_STATUS_CARD_DETECT);
    +       return 1;
     }
     
     static void mxs_mmc_reset(struct mxs_mmc_host *host)

After applying this patch and rebooting, it successfully detects the card,
and continues on its way.  I'm now at the same point with device tree as I
was previously with machine definitions.  I'd still like to know why card
detect was working before, but isn't working now.  Since the goal is to get
the system to a point where network is functioning so we can do quick
cycles using kexec, this'll do for now.

"Fixing" the USB PHY
------------------
Much digging reveals that the clocks are getting created but never
registered.  Applying the following patch at least gets the OTG device to
be addable, though it's not yet working:

    diff --git a/drivers/clk/mxs/clk-imx23.c b/drivers/clk/mxs/clk-imx23.c
    index 844043a..a694237 100644
    --- a/drivers/clk/mxs/clk-imx23.c
    +++ b/drivers/clk/mxs/clk-imx23.c
    @@ -196,6 +196,8 @@ int __init mx23_clocks_init(void)
            clk_register_clkdevs(clks[ssp], ssp_lookups, ARRAY_SIZE(ssp_lookups));
            clk_register_clkdevs(clks[gpmi], gpmi_lookups, ARRAY_SIZE(gpmi_lookups))
            clk_register_clkdevs(clks[lcdif], lcdif_lookups, ARRAY_SIZE(lcdif_lookup
    +       clk_register_clkdev(clks[usb_pwr], NULL, "8007c000.usbphy");
    +       clk_register_clkdev(clks[usb], NULL, "80080000.usbctrl");
     
            for (i = 0; i < ARRAY_SIZE(clks_init_on); i++)
                    clk_prepare_enable(clks[clks_init_on[i]]);

Now we have the USB PHY loading, it's time to figure out why the controller
isn't loading.  An important note here: hardware devices tend to separate
the physical layer from the protocol layers.  Frequently the means by which
a protocol is encapsulated over the wire are sufficiently different and
decoupled from the programmer-facing model that different silicon is used.
There's a USB specification for connecting a USB controller chip to a
separate PHY chip to do the encoding.  So even though the USB PHY is
loading, we only have half of the equation.

In digging through the code, looking through patch comments, and comparing
the i.MX23 to the i.MX28, i.MX6, and i.MX27, it appears as though there are
a large number of problems with our current setup:

* Different device name
* No compatibility for USB controller
* No interrupt specified
* The OTG controller isn't enbaled
* No proper device alias for USB PHY

To fix the USB PHY issue, modify its definition under apbx to look like:

    usbphy: usbphy@8007c000 {
        compatible = "fsl,imx28-usbphy", "fsl,imx23-usbphy";
        reg = <0x8007c000 0x2000>;
        status = "okay";
    };

Then add the following entry to specify the proper definition for
the controller:

    ahb@80080000 {
        usbctrl@80080000 {
            reg = <0x80080000 0x10000>;
            compatible = "fsl,imx6q-usb", "fsl,imx27-usb";
            interrupts = <11>;
            fsl,usbphy = <&usbphy>;
            status = "okay";
        };
    };

Enable the "ChipIdea Highspeed Dual Role Controller" and "ChipIdea host
controller" drivers under USB support, compile, and reload.  If you enable
"USB announce new devices", upon reboot you should now see:

    [    0.580000] ci_hdrc ci_hdrc.0: doesn't support gadget
    [    0.590000] ci_hdrc ci_hdrc.0: ChipIdea HDRC EHCI
    [    0.590000] ci_hdrc ci_hdrc.0: new USB bus registered, assigned bus number 1
    [    0.650000] ci_hdrc ci_hdrc.0: USB 2.0 started, EHCI 1.00
    [    0.650000] usb usb1: New USB device found, idVendor=1d6b, idProduct=0002
    [    0.660000] usb usb1: New USB device strings: Mfr=3, Product=2, SerialNumber=1
    [    0.670000] usb usb1: Product: ChipIdea HDRC EHCI
    [    0.670000] usb usb1: Manufacturer: Linux 3.6.0-rc1-00210-g6f74f1c-dirty ehci_hcd
    [    0.680000] usb usb1: SerialNumber: ci_hdrc.0
    [    0.680000] hub 1-0:1.0: USB hub found
    [    0.690000] hub 1-0:1.0: 1 port detected

Turning on the root hub
-----------------------
At least something is getting detected now.  This board has an LED that
will let me know if the 5V rail is powered, and that particular LED is off.
That means that the USB hub isn't getting any power, which is probably due
to a GPIO switch that's present on the board.  After doing a register
comparison between a stock hacker board and our new kernel, I discover that
one of the GPIOs is, in fact, flipped.  There's a program on the filesystem
called "regutil" that can be used to poke at various registers, and a quick
call out to that fixes things:

    regutil -w HW_PINCTRL_DOE0=0x24011000 -w HW_PINCTRL_DOUT0=0x24011000

Now I have a new problem.  Several times per second, I see the following
message on the console:

    [ 1302.320000] hub 1-0:1.0: unable to enumerate USB device on port 1

Puzzley goodness
----------------
Coincidentally, as I write this there's a separate group trying to get
linux-next running on the same processor, albeit on a different board.
They're experiencing the same problem I am when it comes to USB not
working.  So at least I've managed to replicate their failure.

Now we have a puzzle: The old kernel works, but the new kernel doesn't.
What's changed?  There are two tools we have at our disposal for figuring
things out: Reading code, and enabling debug.

Many source files make calls to dev_dbg() and/or pr_debug() which can emit
lots of juicy internal data structures and messages.  Frequently these can
severely impact performance, so you need to explicitly enable debugging on
a per-file basis.  To enable debugging for a given source file, add the
following line before any headers are included:

    #define DEBUG

After doing this in a few USB source files, I notice the following text
appears on the console whenever a device is connected:

    [   60.400000] mxs_phy 8007c000.usbphy: Connect on port 1

Shortly afterwards, the following text appears:

    [   60.480000] hub 1-0:1.0: unable to enumerate USB device on port 1

Grepping through the source I notice the following string in
drivers/usb/otg/mxs-phy.c:

    dev_dbg(phy->dev, "Connect on port %d\n", port);

Directly below this is the following code:

    writel_relaxed(BM_USBPHY_CTRL_ENHOSTDISCONDETECT,
                    phy->io_priv + HW_USBPHY_CTRL_SET);

If I disable that code, disconnect detect is disabled, and magically USB
starts to work.  So what's probably happening is this sequence of events:

1. The root USB hub connects at USB 1.1 speeds (this is normal)
2. The root USB hub [chirps](http://www.usbmadesimple.co.uk/ums_6.htm#negotiating_high_speed) and disconnects
3. The controller reports this as a disconnect
4. The OTG driver actually disconnects the device
5. Because the device is still attached, it reconnects
6. Goto 1

Turns out there's a bug that must be worked around, and disconnect
detection should remain disabled during negotiation.  At least according to
the manual for this particular part.  This is going to require discussion
with other users of this part, and if possible, people at the chip vendor.

Where are we now
----------------
So after a day's worth of hacking, we've got the following:

1. Linux booting and using device tree
2. Working SD driver, with a card-detect band-aid
3. Working USB, with a host-disconnect band-aid
4. Wifi works, meaning we can start to use kexec

The following things need to be fixed sooner rather than later:

1. Fix card detect properly
2. Fix USB host-disconnect properly
3. Fix USB port power so regutil command is not needed

Next time we'll dig into those problems.  In fact, since we're dealing with
chip errata, the USB host-disconnect problem will probably result in our
first submittable patch.
