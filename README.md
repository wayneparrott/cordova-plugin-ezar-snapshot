#ezAR Snapshot Cordova Plugin
xxxx

##Supported Platforms
- iOS 7, 8 & 9
- Android 4.2 and greater 

##Getting Started
Add the snapshot plugin to your Corodva project the Cordova CLI

        cordova plugin add pathToEzar/com.ezartech.ezar.snapshot

Next in your Cordova JavaScript deviceready handler include the following JavaScript snippet to initialize ezAR and activate the camera on the back of the device.

        ezar.snapshot(
            function(image) {
                //do something with the image
                },
            function(err) {
                alert('snapshot error: ' + err);
                },       
            {"saveToPhotoAlbum":true,
             "encoding": ezAR.ImageEncoding.PNG}
            )
                    
##Additional Documentation        
See [ezartech.com](http://ezartech.com) for documentation and support.

##License
The ezAR Snapshot is licensed under a [modified MIT license](http://www.ezartech.com/ezarstartupkit-license).


Copyright (c) 2015-2016, ezAR Technologies


