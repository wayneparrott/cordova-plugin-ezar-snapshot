/*
 * CDVezARSnapshot.m
 *
 * Copyright 2016, ezAR Technologies
 * http://ezartech.com
 *
 * By @wayne_parrott
 *
 * Licensed under a modified MIT license. 
 * Please see LICENSE or http://ezartech.com/ezarstartupkit-license for more information
 *
 */
 
#import "CDVezARSnapshot.h"
#import "MainViewController.h"

//NSString *const EZAR_ERROR_DOMAIN = @"EZAR_SNAPSHOT_ERROR_DOMAIN";
NSInteger const EZAR_CAMERA_VIEW_TAG = 999;

#ifndef __CORDOVA_4_0_0
#import <Cordova/NSData+Base64.h>
#endif

//copied from cordova camera plugin
static NSString* toBase64(NSData* data) {
    SEL s1 = NSSelectorFromString(@"cdv_base64EncodedString");
    SEL s2 = NSSelectorFromString(@"base64EncodedString");
    SEL s3 = NSSelectorFromString(@"base64EncodedStringWithOptions:");
    
    if ([data respondsToSelector:s1]) {
        NSString* (*func)(id, SEL) = (void *)[data methodForSelector:s1];
        return func(data, s1);
    } else if ([data respondsToSelector:s2]) {
        NSString* (*func)(id, SEL) = (void *)[data methodForSelector:s2];
        return func(data, s2);
    } else if ([data respondsToSelector:s3]) {
        NSString* (*func)(id, SEL, NSUInteger) = (void *)[data methodForSelector:s3];
        return func(data, s3, 0);
    } else {
        return nil;
    }
}

@implementation CDVezARSnapshot
{
   
}


// INIT PLUGIN - does nothing atm
- (void) pluginInitialize
{
    [super pluginInitialize];
}

- (UIImageView *) getCameraView
{
    UIImageView* cameraView = (UIImageView *)[self.viewController.view viewWithTag: EZAR_CAMERA_VIEW_TAG];
    return cameraView;
}

- (BOOL) isVideoOverlayAvailable
{
    return [self getCameraView] == nil;
}

-(BOOL) isCameraRunning
{
    MainViewController *ctrl = (MainViewController *)self.viewController;
    CDVPlugin* videoOverlayPlugin = [ctrl.pluginObjects objectForKey:@"CDVezARVideoOverlay"];
    
    BOOL result = NO;
    
    if (!videoOverlayPlugin) {
        return result;
    }
    
    // Find AVCaptureSession
    NSString* methodName = @"isCameraRunning";
    SEL selector = NSSelectorFromString(methodName);
    result = (BOOL)[videoOverlayPlugin performSelector:selector];
    
    return result;
}

- (AVCaptureStillImageOutput *) getAVCaptureStillImageOutput
{
    MainViewController *ctrl = (MainViewController *)self.viewController;
    CDVPlugin* videoOverlayPlugin = [ctrl.pluginObjects objectForKey:@"CDVezARVideoOverlay"];
    
    if (!videoOverlayPlugin) {
        return nil;
    }
    
    // Find AVCaptureSession
    NSString* methodName = @"getAVCaptureStillImageOutput";
    SEL selector = NSSelectorFromString(methodName);
    AVCaptureStillImageOutput* stillImageOutput =
        (AVCaptureStillImageOutput *)[videoOverlayPlugin performSelector:selector];
    
    return stillImageOutput;
}


// rely on videoOverlay to setup the AVCaptureStillImageOutput when the camera starts. This gives
// the camera time to adjust its light level & white balance; otherwise you get a very dark
// snapshot image if we grab the video frame too quickly.
- (void) snapshot:(CDVInvokedUrlCommand*)command
{
    // Find the videoOverlay CameraView
    UIImageView *cameraView = self.getCameraView;
    if (!cameraView) {
       [self snapshotViewHierarchy:nil cameraView:nil playSound:YES command:command];
        return;
    }
    
    AVCaptureStillImageOutput* stillImageOutput = [self getAVCaptureStillImageOutput];
    
    if (!stillImageOutput || ![self isCameraRunning]) {
        [self snapshotViewHierarchy:nil cameraView:nil playSound:YES command:command];
        return;
    }
 
    
    //
    AVCaptureConnection *videoConnection = nil;
    for (AVCaptureConnection *connection in stillImageOutput.connections) {
        for (AVCaptureInputPort *port in [connection inputPorts]) {
            if ([[port mediaType] isEqual:AVMediaTypeVideo] ) {
                videoConnection = connection;
                break;
            }
        }
        if (videoConnection) { break; }
    }
    
    //workaround for xxx
    //Capture video frame as image. The image will not include the webview content.
    //Temporarily set the cameraView image to the video frame (a jpg) long enough
    //to capture the entire view hierarcy as an image.
    //
    //NSLog(@"about to request a capture from: %@", stillImageOutput);
    [stillImageOutput captureStillImageAsynchronouslyFromConnection: videoConnection
                       completionHandler: ^(CMSampleBufferRef imageBuffer, NSError *error) {
                        
        if (error) {
            
            NSDictionary* errorResult = [self makeErrorResult: 1 withError: error];
                                                          
            CDVPluginResult* pluginResult =
                [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                messageAsDictionary: errorResult];
                                                          
            return  [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
               
                                                      
        NSData *imageData = [AVCaptureStillImageOutput jpegStillImageNSDataRepresentation:imageBuffer];
        UIImage *cameraImage = [[UIImage alloc] initWithData:imageData];
                           
        //rotate image to match device orientation
        switch (self.viewController.interfaceOrientation) {
            case UIInterfaceOrientationPortrait:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationRight];
                break;
            case UIInterfaceOrientationLandscapeLeft:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationDown];
                break;
            case UIInterfaceOrientationPortraitUpsideDown:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationLeft];
                break;
            case UIInterfaceOrientationLandscapeRight:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationUp];
                break;
            
        }
        
        [self snapshotViewHierarchy:cameraImage cameraView:cameraView playSound:NO command:command];
        
    }];
}


- (void) snapshotViewHierarchy:(UIImage*)cameraImage cameraView:(UIImageView*)cameraView 
            playSound:(BOOL)shouldPlaySound command:(CDVInvokedUrlCommand*)command
{
    BOOL saveToPhotoAlbum = [[command argumentAtIndex:1 withDefault:@(NO)] boolValue];
    EZAR_IMAGE_ENCODING encodingType = [[command argumentAtIndex:0 withDefault:@(EZAR_IMAGE_ENCODING_JPG)] unsignedIntegerValue];
    
    saveToPhotoAlbum = YES;
    
    //assign the video frame image to the cameraView image
    if (cameraImage) {
        cameraView.image = cameraImage;
        cameraView.contentMode = UIViewContentModeScaleAspectFill;
    }
    
    if (shouldPlaySound) {
        //solution from http://stackoverflow.com/questions/5430949/play-iphone-camera-shutter-sound-when-i-click-a-button
        AudioServicesPlaySystemSound(1108);
    }
    
    //capture the entire view hierarchy
    UIView *view = self.viewController.view;
    UIGraphicsBeginImageContextWithOptions(view.bounds.size, YES, 0);
    [view drawViewHierarchyInRect: view.bounds afterScreenUpdates: YES];
    UIImage* screenshot = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    //clear camera view image
    if (cameraImage) {
        cameraView.image = nil;
    }
    
    if (saveToPhotoAlbum) { //save image to gallery
        //todo: handling error saving to photo gallery
        UIImageWriteToSavedPhotosAlbum(screenshot, nil, nil, nil);
    }
    
    //format image for return
    NSData *screenshotData = nil;
    if (encodingType == EZAR_IMAGE_ENCODING_JPG) {
        screenshotData = UIImageJPEGRepresentation(screenshot, 1.0);
    } else {
        screenshotData = UIImagePNGRepresentation(screenshot);
    }
    
    CDVPluginResult* pluginResult =
    [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:
     toBase64(screenshotData)];
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

typedef NS_ENUM(NSUInteger, EZAR_ERROR_CODE) {
    EZAR_ERROR_CODE_ERROR=1,
    EZAR_ERROR_CODE_INVALID_ARGUMENT,
    EZAR_ERROR_CODE_INVALID_STATE,
    EZAR_ERROR_CODE_ACTIVATION
};

//
//
//
- (NSDictionary*)makeErrorResult: (EZAR_ERROR_CODE) errorCode withData: (NSString*) description
{
    NSMutableDictionary* errorData = [NSMutableDictionary dictionaryWithCapacity:4];
    
    [errorData setObject: @(errorCode)  forKey:@"code"];
    [errorData setObject: @{ @"description": description}  forKey:@"data"];
    
    return errorData;
}

//
//
//
- (NSDictionary*)makeErrorResult: (EZAR_ERROR_CODE) errorCode withError: (NSError*) error
{
    NSMutableDictionary* errorData = [NSMutableDictionary dictionaryWithCapacity:2];
    [errorData setObject: @(errorCode)  forKey:@"code"];
    
    NSMutableDictionary* data = [NSMutableDictionary dictionaryWithCapacity:2];
    [data setObject: [error.userInfo objectForKey: NSLocalizedFailureReasonErrorKey] forKey:@"description"];
    [data setObject: @(error.code) forKey:@"iosErrorCode"];
    
    [errorData setObject: data  forKey:@"data"];
    
    return errorData;
}

@end
