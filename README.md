




# pi4j-binary-clock
Binary Clock, uses 20 LEDs, I2C communication



1. mvn clean package
2. cd target/distribution
3. ./runBinaryClock.sh -b 0x01 -a1 0x24 -a2 0x22

-b hex value bus    -a1 hex address value PCF8575 (Second/Hour)   " +
" -a2 hex address value PCF8575 (Hours)  -t trace   

-t  trace values : \"trace\", \"debug\", \"info\", \"warn\", \"error\" \n " +
" or \"off\"  Default \"info\" \n"