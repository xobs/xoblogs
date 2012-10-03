---
layout: post
category : firmware
tags : [falconwing, linux, kernel, booting]
---
{% include JB/setup %}

Now that USB and SD are nominally working, we can work on getting them to
actually, really, truly work.  Ideally we'd fix the following problems:

1. USB requires cajoling in order to work
2. We have a home-grown patch on the USB PHY
3. We've hacked the SD driver

Fixing USB power-on
-------------------
The reason USB doesn't work is that there's a transistor on the board that
disables the USB hub.  We could have figured this out by looking at the
schematics, but since I don't have those handy at the moment I simply
compared register dumps between the working kernel and the non-working
kernel.  Through trial and error, I discovered the following command kicks
USB into working:

    regutil -w HW_PINCTRL_DOUT0_SET=0x20000000 -w HW_PINCTRL_DOE0_SET=0x20000000

Therefore, we know that bank 0, pin 29 is the switch that controls the hub.
We can create a regulator that toggles this pin at boot so that the hub is
enabled.  Add the following definition to the "regulators" section of the
device tree table:

    reg_usbctrl_vbus: usbctrl_vbus {
        compatible = "regulator-fixed";
        regulator-name = "usbctrl_vbus";
        regulator-min-microvolt = <5000000>;
        regulator-max-microvolt = <5000000>;
        regulator-boot-on;
        regulator-always-on;
        gpio = <&gpio0 29 0>;
        enable-active-high;
    };

Then, add the following attribute to the usbctrl definition:

    vbus-supply = <&reg_usbctrl_vbus>;

After making this change, we see that USB works at boot.

Fixing USB hub detect
---------------------
USB hub detect turned out to be much more complicated, mostly due to the
fact that we'd like to submit a patch.  After subscribing to linux-usb and
discussing on-list with a wide variety of people, including those from
Freescale, the following patch was proposed:

 Suggested-by: Sean Cross <xobs@xoblo.gs>
 Signed-off-by: Fabio Estevam <fabio.estevam@freescale.com>
 ---
  drivers/usb/otg/mxs-phy.c |   12 ++++++++----
  1 file changed, 8 insertions(+), 4 deletions(-)
  
 diff --git a/drivers/usb/otg/mxs-phy.c b/drivers/usb/otg/mxs-phy.c
 index c1a67cb..c70d026 100644
 --- a/drivers/usb/otg/mxs-phy.c
 +++ b/drivers/usb/otg/mxs-phy.c
 @@ -20,6 +20,7 @@
  #include &lt;linux/delay.h>
  #include &lt;linux/err.h>
  #include &lt;linux/io.h>
 +#include &lt;linux/of_platform.h>
  
  #define DRIVER_NAME "mxs_phy"
  
 @@ -81,8 +82,10 @@ static int mxs_phy_on_connect(struct usb_phy \*phy, int port)
     dev_dbg(phy->dev, "Connect on port %d\n", port);
  
     mxs_phy_hw_init(to_mxs_phy(phy));
 -   writel_relaxed(BM_USBPHY_CTRL_ENHOSTDISCONDETECT,
 -           phy->io_priv + HW_USBPHY_CTRL_SET);
 +
 +   if (!of_machine_is_compatible("fsl,imx23"))
 +       writel_relaxed(BM_USBPHY_CTRL_ENHOSTDISCONDETECT,
 +               phy->io_priv + HW_USBPHY_CTRL_SET);
  
     return 0;
  }
 @@ -91,8 +94,9 @@ static int mxs_phy_on_disconnect(struct usb_phy \*phy, int port)
  {
     dev_dbg(phy->dev, "Disconnect on port %d\n", port);
  
 -   writel_relaxed(BM_USBPHY_CTRL_ENHOSTDISCONDETECT,
 -           phy->io_priv + HW_USBPHY_CTRL_CLR);
 +   if (!of_machine_is_compatible("fsl,imx23"))
 +       writel_relaxed(BM_USBPHY_CTRL_ENHOSTDISCONDETECT,
 +               phy->io_priv + HW_USBPHY_CTRL_CLR);
  
     return 0;
  }
 -- 
 1.7.9.5

Basically, it checks device tree to see if the machine is an i.MX233, and
if so, it doesn't fiddle with the ENHOSTDISCONDETECT bit in the PHY
register.  I would have thought this would break on devices that rely on
working disconnect detection on the root device, but apparently it works
fine for most everyone else.  As of right now, the patch hasn't yet landed,
but I'll proceed as if it had.

Fixing MMC
----------
Still working on this one.  For now, I'm going to leave my hack in place.
Since I only need this for a quick hack, I'm going to just say that for
now, it's okay.


Final Output
------------
I've wrapped up the changes I've made in a single patchfile called
[falconwing.patch](/files/falconwing.patch).  It contains the new Device
Tree definition for the Falconwing board, as well as patches for the MMC
card, the USB PHY, and USB clocks.  It can be applied to revision
fea7a08acb13524b47711625eebea40a0ede69a0 of Linux.

Future work will be done with this kernel, but for now we've essentially
got what we're looking for: A board with a lot of GPIOs running a recent
version of Linux.  Next up, we need to get actual hardware working.
is stored 
