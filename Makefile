.PHONY : last

q = cat naturgy.csv | docker run -i --rm yandex/clickhouse-client:21.7.3.14 local --format_csv_allow_single_quotes false --date_time_input_format best_effort --input-f CSVWithNames -S "date Date,from UInt16,type String,kWh Float64,unit_price Float64,amount Float64" -q "$1"

kwh_unit = select date,from,amount/kWh from table where date=='$1' order by from asc

power = select sum(power) from (select 5.75*multiIf(toDayOfWeek(date) between 1 and 5,0.039835+0.059450,0.059450) as power from table where date>='$1' group by date)

energy = select sum(kWh)*0.135000 from table where date>='$1' 

medium_kwh_cost = select sum(amount)/sum(kWh) from table where date>='$1'

query:
	$(call q, "$(QUERY)")

last: 
	$(call q, $(call medium_kwh_cost,2021/11/18))

power:
	$(call q, $(call power,2021/11/18))

energy:
	$(call q, $(call energy,2021/11/18)) 

test:
	$(call q, $(call kwh_unit,2021/12/12))

preview:
	quarto preview site/electricity.qmd

