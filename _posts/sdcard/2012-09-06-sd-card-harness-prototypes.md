---
layout: post
category : sdcard
tags : [falconwing, hardware, solder]
---
For the very first prototype, we just need an SD slot.  There are a few
approaches we can take, but the most flexible is to create an external
board and run wires to it.  In the future, we'll set up a jig to be able
to probe NAND flash pins, but for now we just want to write our
SD-over-SPI card stimulation software, and make sure that it's reliable.
With that in mind, it's time to break out the soldering iron.

First Attempt: Spaghetti
------------------------
My initial attempt wasn't the cleanest solder job in the world.  I took a
pair of cutters to a chumby 8, and ripped out its SD card slot.  Then I
soldered flywires from various points of interest on an NeTV directly to
pins on the bare slot.  Then I could configure those pins as GPIOs and
talk to an SD card.

The result wasn't very pretty:

![Mess-of-wires](/images/sd-harness-prototype-v1-small.jpg)

This approach suffered from several problems.  Aside from the fragile
wiring, there were only four data wires (MISO, MOSI, SCLK, and CSEL),
meaning I couldn't implement the full spec later on.  There also wasn't a
way to switch power, so if (when) I crashed the SD card, there wasn't any
easy way to reset it.

Cleaner Salvage
---------------
Having learned that flywires aren't a long-term solution, I've decided to
use a ribbon cable in my next attempt.  This will go to a board with a
different SD slot, and will have a power switch to interrupt the 3.3V
line.

To start with, I pulled an SD slot off of a $2 USB adapter.  This slot has
the advantage of being very short, which is good for accessing the pins
later on.  The slot was glued down to a piece of proto board using epoxy,
and let to sit overnight.

The next day I attached a 90-degree 8x2 header and attached most of the
pins directly to the card slot.  Some connections weren't very convenient,
and so I needed to bring out more flywires.  I added a switch to this
design, which let me manually toggle the power supply to the card.

The result was only slightly prettier:

![Mess-of-wires](/images/sd-harness-prototype-v2-small.jpg)

Unfortunately, the legs had been sheared
off when the slot was removed from the board, and one or two of the
critical connections were unreliable during normal operation.  When doing
low-level code bringup such as working with SD cards in SPI mode, it helps
to be able to rely on hardware, so a flaky connection can be maddening.
Therefore, this second attempt was scrapped as well.

Cleanest Perfboard
------------------
Adapting the previous idea of using perfboard and a ribbon cable, the third
prototyping attempt used a 90-degree 8x2 header.  This board moved up to
using a new-in-box SD slot purchased online, and added a switchable power
supply for when things go wrong.

Learning again from previous flywire attempts, this revision made sure to
provide strain-relief everywhere and be as clean as possible.  Without
planning the layout in advance, I did accidentally manage to back myself
into a corner for the ground plane, but overall the board didn't look too
bad:

![Mess-of-wires](/images/sd-harness-prototype-v3-small.jpg)

Best of all, the thing appears to work, and *reliably* at that.  With the
hardware stable, it's onwards and upwards to software.
