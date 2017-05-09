---
layout: documentation
---

{% include base.html %}

# ZigBee Binding
The ZigBee binding supports an interface to a wireless ZigBee home automation network. 


## Supported Things

### ZigBee TI2531 Coordinator

Before the binding can be used, a coordinator must be added. This needs to be done manually and the serial port must be set.


## Discovery

Once the binding is authorized, and an adapter is added, it automatically reads all devices that are set up on the ZigBee controller and puts them in the Inbox.

## Binding Configuration



## Thing Configuration

The binding will attempt to automatically detect new devices, and will read their supported clusters upon startup. A set of channels will then be created depending on what clusters and endpoints a device supports.

The following devices have been tested with the binding -:




## Channels


