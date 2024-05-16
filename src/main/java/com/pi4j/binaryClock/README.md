





1. mvn clean package
2. cd target/distribution
3. sudo ./runBinaryClock.sh -b 0x01 -a1 0x24 -a2 0x22 

-b hex value bus    -a1 hex address value second PCF8575 (Second/Hour)   " +
" -a2 hex address value first PCF8575 (Hours)  -t trace   