---
layout: post
category : firmware
tags : [falconwing, linux, kernel, booting]
---
{% include JB/setup %}

Now that USB and SD are nominally working, we can work on getting them to
actually, really, truly work.

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

Now when the system boots USB will come up.  Case closed.

Or is it?  I've made some noise on the linux-usb mailing list asking for the
best way to permanently fix the problem.  There have been responses from
some of the chip designers, and a nice discussion has been going.  It's a
great example of open-source in action.

For now, I'm going to take a break from the port to allow this problem to be
solved.
