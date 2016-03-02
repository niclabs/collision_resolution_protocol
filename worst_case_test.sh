#!/bin/bash
#for ((i=1; i<=$1; i++))
for i in $(seq 1 $1);
do
	./gradlew run -PappArgs=[$RANDOM,$1] > logs/log$i.txt &
	pids[$i]=$!
done

for pid in ${pids[*]}
do
	wait $pid
done