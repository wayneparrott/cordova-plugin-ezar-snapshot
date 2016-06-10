/**
 * CDVezARSnapshot.h
 *
 * Copyright 2016, ezAR Technologies
 * http://ezartech.com
 *
 * By @wayne_parrott
 *
 * Licensed under a modified MIT license. 
 * Please see LICENSE or http://ezartech.com/ezarstartupkit-license for more information
 */

#import <AVFoundation/AVFoundation.h>
#import <AudioToolbox/AudioToolbox.h>

#import "Cordova/CDV.h"

/**
 * Implements the ezAR Cordova api. 
 */
@interface CDVezARSnapshot : CDVPlugin

- (void) snapshot:(CDVInvokedUrlCommand*)command;
- (void) saveToPhotoGallery:(CDVInvokedUrlCommand*)command;

@end

typedef NS_ENUM(NSUInteger, EZAR_IMAGE_ENCODING) {
    EZAR_IMAGE_ENCODING_JPG=0,
    EZAR_IMAGE_ENCODING_PNG
};



