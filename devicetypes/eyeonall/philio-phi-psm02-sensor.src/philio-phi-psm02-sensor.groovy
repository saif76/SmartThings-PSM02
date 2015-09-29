/*
 * Philio PSM02 4-in-1 Multi Sensor Device Type
 * Based on My PSM01 Sensor created by SmartThings/Paul Spee
 * which is based on SmartThings' Aeon Multi Sensor Reference Device Type
 */
 
 metadata {


	definition (name: "Philio PHI_PSM02 Sensor", namespace: "eyeonall", author: "AJ") {
		capability "Contact Sensor"
        capability "Motion Sensor"
		capability "Temperature Measurement"
		capability "Illuminance Measurement"
		capability "Configuration"
		capability "Sensor"
		capability "Battery"
        capability "Refresh"
		capability "Polling"
		fingerprint deviceId: "0x2001", inClusters: "0x80,0x85,0x70,0x72,0x86,0x30,0x31,0x84,0xEF,0x20"
        
	}

	tiles {
    
        
		standardTile("contact", "device.contact", width: 2, height: 2) {
			state "closed", label: 'Closed', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
			state "open", label: 'Open', icon: "st.contact.contact.open", backgroundColor: "#ffa81e"
		}

		standardTile("motion", "device.motion", width: 2, height: 2) {
			state "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
			state "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
		}
       
       valueTile("temperature", "device.temperature", inactiveLabel: false) {
			state "temperature", label:'${currentValue}°',
			backgroundColors:[
				[value: 31, color: "#153591"],
				[value: 44, color: "#1e9cbb"],
				[value: 59, color: "#90d2a7"],
				[value: 74, color: "#44b621"],
				[value: 84, color: "#f1d801"],
				[value: 95, color: "#d04e00"],
				[value: 96, color: "#bc2323"]
			]
		}

		valueTile("illuminance", "device.illuminance", inactiveLabel: false) {
			state "luminosity", label:'${currentValue} ${unit}', unit:"lux"
		}
        
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}
        
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		standardTile("refresh", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
			state "default", action:"polling.poll", icon:"st.secondary.refresh"
		}

		main(["contact", "motion", "temperature", "illuminance"])
		details(["contact", "motion", "temperature", "illuminance", "battery", "configure", "refresh"])
		}

		preferences {
			input description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter \"-5\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input "tempOffset", "number", title: "Temperature Offset", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
		}

}

preferences {
}

def installed() {
	log.debug "PSM02: Installed with settings: ${settings}"
	configure()
}

def updated() {
	log.debug "PSM02: Updated with settings: ${settings}"
    configure()

}

// parse() with a Map argument is called after a sendEvent(device)
// In this case, we are receiving an event from the PSM01 Helper App to generate a "inactive" event
def parse(Map evt){
//	log.debug "Parse(Map) called with map ${evt}"
    def result = [];
    if (evt)
    	result << evt;
    //log.debug "Parse(Map) returned ${result}"
	def statusTextmsg = ""
    statusTextmsg = "Door is ${device.currentState('contact').value}, temp is ${device.currentState('temperature').value}°, and illuminance is ${device.currentState('illuminance').value} LUX."
    sendEvent("name":"statusText", "value":statusTextmsg)
    //log.debug statusTextmsg

    return result
}

// Parse incoming device messages to generate events
def parse(String description)
{
    //log.debug "PSM02 Parse called with ${description}"
	def result = []
	def cmd = zwave.parse(description, [0x20: 1, 0x30: 2, 0x31: 5, 0x70: 1, 0x72: 2, 0x80: 1, 0x84: 2, 0x85: 2, 0x86: 1])
	//log.debug "PSM02 Parsed CMD: ${cmd.toString()}"
    if (cmd) { 
		if( cmd.CMD == "8407" ) { result << new physicalgraph.device.HubAction(zwave.wakeUpV2.wakeUpNoMoreInformation().format()) }
		def evt = zwaveEvent(cmd)
        result << createEvent(evt)
	}
    def statusTextmsg = ""
    statusTextmsg = "Door is ${device.currentState('contact').value}, temp is ${device.currentState('temperature').value}°, and illuminance is ${device.currentState('illuminance').value} LUX."
    sendEvent("name":"statusText", "value":statusTextmsg)
    //log.debug statusTextmsg
	//log.debug "PSM02 Parse returned ${result}"
	return result
}

// Event Generation
def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
	//log.debug "PSM02: WakeUpNotification ${cmd.toString()}}"

	[descriptionText: "${device.displayName} woke up", isStateChange: false]
    
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
	//log.debug "PSM02: SensorMultilevel ${cmd.toString()}"
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			map.name = "temperature"
            if (tempOffset) {
				def offset = tempOffset as int
				def v = map.value as int
				map.value = v + offset
			}
            //log.debug "Adjusted temp value ${map.value}"
			break;
		case 3:
			// luminance
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "lux"
			map.name = "illuminance"
			break;
	}
	//map
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	//log.debug "PSM02: BatteryReport ${cmd.toString()}}"
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	//map
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
    log.debug "PSM02: SensorBinaryReport ${cmd.toString()}}"
    def map = [:]
    switch (cmd.sensorType) {
        case 10: // contact sensor
            map.name = "contact"
            log.debug "PSM02 cmd.sensorValue: ${cmd.sensorValue}"
            if (cmd.sensorValue.toInteger() > 0 ) {
            	//log.debug "PSM02 DOOR OPEN"
                map.value = "open"
                map.descriptionText = "$device.displayName is open"
            } else {
            	//log.debug "PSM02 DOOR CLOSED"
                map.value = "closed"
                map.descriptionText = "$device.displayName is closed"
            }
            break;
        case 12: // motion sensor
            map.name = "motion"
            log.debug "PSM02 cmd.sensorValue: ${cmd.sensorValue}"
            if (cmd.sensorValue.toInteger() > 0 ) {
            	//log.debug "PSM02 Motion Detected"
                map.value = "active"
                map.descriptionText = "$device.displayName is active"
            } else {
            	//log.debug "PSM02 No Motion"
                map.value = "inactive"
                map.descriptionText = "$device.displayName no motion"
            }
            map.isStateChange = true
            break;
    }
    //map
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "PSM02: Catchall reached for cmd: ${cmd.toString()}}"
	[:]
}

def configure() {
    log.debug "PSM02: configure() called"
    
	delayBetween([
		
        //1 tick = 30 minutes
        zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 70).format(), // PIR Sensitivity 1-100
        zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: 3).format(), // PIR Redetect Interval. Applies only if in security mode
		zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 1).format(), // Auto report Battery time 1-127, default 12
		zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: 2).format(), // Auto report Door/Window state time 1-127, default 12
		zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: 2).format(), // Auto report Illumination time 1-127, default 12
        zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: 2).format(), // Auto report Temperature time 1-127, default 12
        zwave.wakeUpV1.wakeUpIntervalSet(seconds: 1 * 3600, nodeid:zwaveHubNodeId).format(),						// Wake up every hour

    ])
}

