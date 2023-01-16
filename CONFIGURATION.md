# Configuration

There are two main configuration files under the `conf` folder.

<pre >
conf
├── config.properties         // main configuration file, options are well documented within.
└── queries.json              // queries/graphs to be performed periodically.
</pre>

Both files need to be edited, the default options will not suffice.

Datasources and other important options are available in `config.properties`.

Graphs for queries in the `queries.json` file will be regenerated periodically 
according to the **replotInterval** parameter in `config.properties`.

Graphs for queries not in the `queries.json` file will use the default query 
type and wave server as configured in `config.properties`.

Queries in the `queries.json` file must have the following json structure:

```
{
    "type": "fdsn",
    "S": "JULI",
    "C": "HHZ",
    "N": "LI",
    "L": "00",
    "graphFormat": "svg",
    "windowSize": 4096,
    "windowFunction": "hann",
    "responseFactor": 1.0
}
```
`type` can be **fdsn**, **seedlink** or **winston**. The proper wave server will
be used as configured in `config.properties`. If not present, the default will
be used.

`graph` can be **svg** or **png**. If not present, the default will be used.

`windowSize`is the window size for the FFT and its value must be a power of 2, 
the higher this number, the more frequency resolution you will have, at the cost 
of lower temporal resolution, and viceversa. This value also determines the 
temporal window  for rsam computations. As an example, for a trace with 100 sps 
and a window size of  4096, the temporal resolution will be 40.96 seconds and the
frequency resolution will  be aproximately 0.24 Hz.

`windowFunction` can be **uniform** or **hann** (default).

`responseFactor` is the multiplier used to convert counts to nm/s.

Additionally, queries can have the following properties:

`maxPower` for setting the maximum power in the ssam color scale.

`cutOff` Sets the cutoff frequency.

Graphs for queries in the `queries.json` fiel will be generated sequentially in 
order to not generate unnecesary hight load on the wave servers.