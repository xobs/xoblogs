---
layout: post
category : netv
tags : [hardware, netv, hacks]
---

This year I will again be participating in the [Indie Games for
Good](http://iggmarathon.com/) charity gaming marathon, which involves
playing videogames on-camera for days at a time.  There's a whole
off-camera support crew, but when they want to send a message to people
playing the games it can be a challenge to get their attention.  This year
we're just going to overlay a UI on top of the HDMI stream going to the TV,
which will make it very easy to pass information to people in such a way
that it can't be ignored.


About the Marathon
------------------
This particular marathon raises money by accepting donations for the
[Child's Play Charity](http://childsplaycharity.org/), and the more people
donate the longer the marathon lasts.  As an added bonus, when you donate
you can request we play a specific game.  It's important to be able to let
people on-camera know when to switch to a new game, and which game they're
switching to.  Also, it makes it possible to cut them off in case they go
over time.

Since the stream is online, we actually have a chatroom where people can
talk with us and give us feedback in realtime.  It's helpful to be able to
see what people are saying, and people off-camera can monitor chat and find
interesting tidbits to bring to our attention.  Being able to talk directly
to viewers in real-time by making certain those playing the games know
what's going on in the chat will help keep the viewers engaged.

Finally, it's a marathon.  Knowing when a donation happens, knowing what
our current total is, and knowing when we pass milestones is just plain
useful.


About the Hardware
------------------
The base hardware is an
[NeTV](http://kosagi.com/w/index.php?title=NeTV_Main_Page).  As a
disclaimer, I helped to get running, and so I'm rather familiar with it as
a platform.  It is unique in that it can overlay content onto a live
HDMI stream, even if it's encrypted.  The only limitation is that
it can't go above around a 100 MHz pixclock, meaning it can't do 1080p60
(but 1080p24 and 720p60 are fine).  That's okay, as our stream is 720p
anyway.

About the Software
------------------
The software that runs on NeTV is built using [OpenEmbedded
classic](http://kosagi.com/w/index.php?title=Building_OpenEmbedded).  The
software presents a framebuffer at /dev/fb0, and any pixel that is of a
certain shade of pink (240,0,240) will be replaced by a pixel from the
incoming HDMI stream.  This makes it very easy to write overlay programs.

We can use OpenEmbedded to build a replacement SD image that has Xorg
and [freerdp](http://www.freerdp.com/) rather than the chumby NeTV browser.
We can use VirtualBox to act as an RDP server.
