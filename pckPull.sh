adb shell rm /sdcard/capture.pcap
adb shell tcpdump -i any -p -s 0 -w /sdcard/capture.pcap

