# gesture-drumkit
a real-time gesture based interactive drum kit

### Setup
Install respective sdks. Watch sdk is 2.3.2 (check the files to verify)
If using android emulator, ensure resolution is 1080 x 2340 pixels, or a similar aspect ratio

### Communication Protocol
The watch app & android app communicate through protobuf.  
The watch app in particular uses [Nanopb](https://github.com/nanopb/nanopb).  
Use the Nanopb binary to compile .proto files in C. (see the readme at github for more info)  

Things to note:  
Both watch app & android app use the same \*.proto files.  
\*.proto files should be symlinked from AndroidApp to WatchApp.  

Watch app's \*.proto needs extra configuration if the \*.proto includes variable length fields 
like strings, bytes or repeated fields.  
Specify the max number of repetitions, or max size in an accompanying \*.options file.  
Do not forget to edit the \*.options file when editing the \*.proto files  

### Technicalities 
The android app is a `producer`, while the watch app is a `consumer` under Samsung's terminology. This distinction is found in the code for inter-device communication. See Samsung's official programming [guide](https://developer.samsung.com/galaxy/accessory/guide#) for more info.  

### Sample Playback
The current mixer implementation on the phone supports ONLY 48,000Hz, 16bit, stereo(2 channels) `.wav` files. To convert from other formats, use the command below:
> ffmpeg -i splash.wav -ar 48000 -sample_fmt s16 -ac 2 splash-new.wav
