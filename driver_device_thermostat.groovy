preferences {
	input("email", "text", title: "E-mail", description: "Your neviweb® account login e-mail")
	input("password", "password", title: "Password", description: "Your neviweb® account login password")
	input("gatewayname", "text", title: "Network Name:", description: "Name of your neviweb® network")
	input("devicename", "text", title: "Device Name:", description: "Name of your neviweb® thermostat")
}

metadata {
	definition (name: "Sinope technologie Thermostat", namespace: "Sinope Technologie", author: "Mathieu Virole") {
		capability "Polling"
		capability "Thermostat"
		capability "Temperature Measurement"
		
		capability "Sensor"
        
		command "heatingSetpointUp"
		command "heatingSetpointDown"

		attribute "temperatureUnit", "string"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"temperature", type: "lighting", width: 6, height: 4, canChangeIcon: true, decoration: "flat"){
			tileAttribute ("device.temperature", key: "PRIMARY_CONTROL") {
				attributeState("temperature", label:'${currentValue}°',backgroundColor:"#44B621")
			}
            tileAttribute ("device.thermostatOperatingState", key: "SECONDARY_CONTROL") {
           		attributeState("thermostatOperatingState", label:'									Heating power: ${currentValue}%')       		
            }
		}  

		//Heating Set Point Controls
        standardTile("heatLevelUp", "device.heatingSetpoint", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "heatLevelUp", action:"heatingSetpointUp", icon:"st.thermostat.thermostat-up"
        }
		standardTile("heatLevelDown", "device.heatingSetpoint", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "heatLevelDown", action:"heatingSetpointDown", icon:"st.thermostat.thermostat-down"
        }
        valueTile("heatingSetpoint", "device.heatingSetpoint", width: 2, height: 2, inactiveLabel: false) {
			state "heat", label:'${currentValue}°', backgroundColor:"#153591"
		}
		
		main (["temperature"])
        details(["temperature", "heatLevelUp", "heatingSetpoint", "heatSliderControl", "heatLevelDown"])
	}
}

def setHeatingSetpoint(temp) {

	if(!isLoggedIn()) {
		log.info "Need to login"
		login()
	}
	
	def temperatureUnit = device.latestValue('temperatureUnit')
	def temperature

	if (temp!=null){
		temp=temp.toDouble().round(2)
		log.info("setHeatingSetpoint -> Value :: ${temp}° ${temperatureUnit}")
	}else{
		temp=null
	}
	

	switch (temperatureUnit) {
		case "celsius":
			sendEvent(name: 'heatingSetpoint', value: temp, unit: temperatureUnit, state: "heat")
         	temperature = temp    
        break;

        case "fahrenheit":
        	sendEvent(name: 'heatingSetpoint', value: temp, unit: temperatureUnit, state: "heat")
			temperature = fToC(temp)
		break;
    }

    log.info("setHeatingSetpoint _ STEP2 -> NEW Value :: ${temp}° ${temperatureUnit}")

	def params = [
		uri: "https://neviweb.com/api/device/${data.deviceId}/setpoint",
		headers: ['Session-Id' : data.auth.session],
	 	body: ['temperature': temperature]
	]

    httpPut(params){resp ->
      resp.data
      log.info("setHeatingSetpoint -> API response :: ${resp.data}") 
    }

    runIn(10, poll)        
}		

def heatingSetpointUp(){

	if(!isLoggedIn()) {
		log.info "Need to login"
		login()
	}
	
	def temperatureUnit = device.latestValue('temperatureUnit')
	
	def newSetpoint
	
	switch (temperatureUnit) {
		
		case "celsius":
	        newSetpoint = device.currentValue("heatingSetpoint") + 0.5
	        if (newSetpoint >= 30) {
				newSetpoint = 30
			}     
	    break;

	    case "fahrenheit":
			newSetpoint = device.currentValue("heatingSetpoint") + 1
			if (newSetpoint >= 86) {
				newSetpoint = 86
			} 
		break;
	}
		
	log.warn("Setpoint UP -> New Value :: ${newSetpoint}° ${temperatureUnit}")
	
	setHeatingSetpoint(newSetpoint)
}

def heatingSetpointDown(){

	if(!isLoggedIn()) {
		log.info "Need to login"
		login()
	}
	
	def newSetpoint
	
	def temperatureUnit = device.latestValue('temperatureUnit')
	switch (temperatureUnit) {
			
			case "celsius":
	         	newSetpoint = device.currentValue("heatingSetpoint") - 0.5
	         	if (newSetpoint <= 5) {
					newSetpoint = 5
				}      
	        break;
	       
	        default:
				 newSetpoint = device.currentValue("heatingSetpoint") - 1
				 if (newSetpoint <= 41) {
					newSetpoint = 41
				}  
			break;
	}
		
	log.warn("Setpoint DOWN -> New Value :: ${newSetpoint}° ${temperatureUnit}")
		
	setHeatingSetpoint(newSetpoint)
}

def manual() {
	setMode('manual')
}

def setMode(mode) {

	if(!isLoggedIn()) {
		log.info "Need to login"
		login()
	}
    
	def code
    
	switch (mode) {
		case "off":
        	log.warn("off")
        	code=0
        break;
        case "auto":
        	log.warn("auto")
        	code=3
        break;
        case "manual":
        	log.warn("manual")
        	code=2
        break; 
    }

    def params = [
		uri: "https://neviweb.com/api/device/${data.deviceId}/mode",
        headers: ['Session-Id' : data.auth.session],
        body: ['mode': code]
    ]
    
    httpPut(params)	{resp ->
		resp.data
        log.info("Mode set -> API response :: ${resp.data}")
    }

	poll()	
}

def poll() {

	if(!isLoggedIn()) {
		login()
	}

	def temperature
    def heatingSetpoint
    def range
	def temperatureUnit = device.latestValue('temperatureUnit')

    def params = [
		uri: "https://neviweb.com/api/device/${data.deviceId}/data?force=1",
		requestContentType: "application/x-www-form-urlencoded; charset=UTF-8",
        headers: ['Session-Id' : data.auth.session]
    ]
     
    httpGet(params) {resp ->
		data.status = resp.data
    }

	log.info("Data device is :: ${data.status}")

    if(data.auth.user.format.temperature == "c"){
    	temperatureUnit = "celsius"
    }else{
    	temperatureUnit = "fahrenheit"
    }
    
    sendEvent(name: "temperatureUnit",   value: temperatureUnit)
    
    switch (temperatureUnit) {

        case "celsius":
        	log.info("celsius temperature")
        	temperature = FormatTemp(data.status.temperature)
        	heatingSetpoint = FormatTemp(data.status.setpoint)
        break;

        case "fahrenheit":
        	log.info("fahrenheit temperature")
        	temperature = FormatTemp(data.status.temperature)
        	heatingSetpoint = FormatTemp(data.status.setpoint)
        break;
    }

    if(data.status.mode!=2){
    	manual()
    }
    
	sendEvent(name: 'temperature', value: temperature, unit: temperatureUnit, state: temperatureType)	
	sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, unit: temperatureUnit, state: "heat")
    sendEvent(name: 'thermostatOperatingState', value: "${data.status.heatLevel}")
    
	runIn(15, poll)
	logout()
}

def login() {

    def params = [
        uri: 'https://neviweb.com',
        path: '/api/login',
        requestContentType: "application/x-www-form-urlencoded; charset=UTF-8",
        body: ["email": settings.email, "password": settings.password, "stayConnected": "0"]
    ]

    httpPost(params) { response ->
        data.auth = response.data
        log.info("login and password :: OK")
        log.warn("data.auth")
        gatewayId()
    }
}

def logout() {

      	def 	params = [
		uri: "https://neviweb.com",
        path: "/api/logout",
       	requestContentType: "application/x-www-form-urlencoded; charset=UTF-8",
        headers: ['Session-Id' : data.auth.session]
    	]
        
        httpGet(params) {resp ->
			data.auth = resp.data
        }
        log.info("logout :: OK")  
}

def gatewayId(){

	def params = [
		uri: "https://neviweb.com",
        path: "/api/gateway",
       	requestContentType: "application/json, text/javascript, */*; q=0.01",
        headers: ['Session-Id' : data.auth.session]
    ]

    httpGet(params) { response ->
        data.gateway_list = response.data
    }

    def gatewayName=settings.gatewayname
	gatewayName=gatewayName.toLowerCase().replaceAll("\\s", "")

	for(var in data.gateway_list){

    	def name_gateway=var.name
    	name_gateway=name_gateway.toLowerCase().replaceAll("\\s", "")

    	if(name_gateway==gatewayName){
    		data.gatewayId=var.id
    		log.info("gateway ID is :: ${data.gatewayId}")
    		deviceId()
    	}
    }
}

def deviceId(){

	def params = [
		uri: "https://neviweb.com",
        path: "/api/device",
        query: ['gatewayId' : data.gatewayId],
       	requestContentType: "application/json, text/javascript, */*; q=0.01",
        headers: ['Session-Id' : data.auth.session]
   	]

    httpGet(params) {resp ->
		data.devices_list = resp.data
    }

    def deviceName=settings.devicename
	deviceName=deviceName.toLowerCase().replaceAll("\\s", "")

	log.warn("Device name forms :: ${deviceName}")

    for(var in data.devices_list){

    	def name_device=var.name
    	name_device=name_device.toLowerCase().replaceAll("\\s", "")
    	
    	if(name_device==deviceName){
    		data.deviceId=var.id
    		log.info("device ID is :: ${data.deviceId}")
    	}
    }	
}

def isLoggedIn() {

	log.info ("Is it login?")

	if (data?.auth?.session!=null){
		try{
			def params = [
				uri: "https://neviweb.com",
			    path: "/api/gateway",
			   	requestContentType: "application/json, text/javascript, */*; q=0.01",
			    headers: ['Session-Id' : data.auth.session]
			]
				
			httpGet(params) {resp ->
			    if(resp.data.sessionExpired==true){
			    	log.info "No session Expired"
			    	data.auth=""
			    }
			}

			if(!data.auth) {
				return false
				log.error("not pass log")
			} else {
				if (data?.deviceId!=null){
					return true
				}else{
					return false
					log.error("not device or gateway with this name")
				}
			}

		}catch (e){
			log.error(e)
			return false
		}
	}else{
		return false
	}
	
}

def FormatTemp(temp){
	
	def temperatureUnit = device.latestValue('temperatureUnit')

	if (temp!=null){

		float i=Float.valueOf(temp)

		switch (temperatureUnit) {
	        case "celsius":
				return (Math.round(i*2)/2).toDouble().round(2)
				log.warn((Math.round(i*2)/2).toDouble().round(2))
	        break;

	        case "fahrenheit":
	        	return (Math.ceil(cToF(i))).toDouble().round(2)
	        	log.warn(Math.ceil(cToF(i)).toDouble().round(2))
	        break;
	    }
    }else{

    	return null
    }
}

def cToF(temp) {
	return ((( 9 * temp ) / 5 ) + 32)
	log.info "celsius -> fahrenheit"
}

def fToC(temp) {
	return ((( temp - 32 ) * 5 ) / 9)
	log.info "fahrenheit -> celsius"
}
