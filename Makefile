.PHONY : last

q = cat naturgy.csv | docker run -i --rm yandex/clickhouse-client:21.7.3.14 local --format_csv_allow_single_quotes false --date_time_input_format best_effort --input-f CSVWithNames -S "date Date,from UInt16,type String,kWh Float64,unit_price Float64,amount Float64" -q $1

last: 
	$(call q, "select sum(amount)/sum(kWh) from table where date>='2021/11/18'")
