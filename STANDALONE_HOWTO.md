# Running Standalone

### Prerrequisites

* Java 16 or later.
* Gnuplot 5 or later. If possible, add the pngcairo terminal, usually not 
  included in the default distribution.

As long as the above dependencies are installed and available on the PATH, any 
Linux or Unix should be fine, no idea is there is a way to run this on Windows.


### Steps

* Download the packaged rsam-ssam and place it in a folder of your choice.

* Customize `config.properties` and `queries.json` files.
* Execute `./run.sh start`.
* Check the logs and verify no erros are being logged.
* Check the web interface available on port 19090.
* Periodically generated graphs will be eventually available under the `output` 
  folder and organized chronologically, be prepared to wait a little bit for 
  this since the scheduller tries to schedule graph generation on nice round 
  minutes, for example, if `replotInterval` is set to 15 minutes, then 
  executions will be scheduled at --:00, --:15, --:30 and --:45. Graphs 
  generated via the web interface will be available inside folder `output/web`.
  
* For stopping rsam-ssam execute `./run.sh stop`.
  
Every graph will be acompannied by the data that backups the graph, in case 
there is a need to plot the data separately. However, overtime these files will 
requiere lots of disk space, so it is strongly advised to keep and eye on this 
folder a clean csv files as needed. In the future there  will be an option for 
scheduling an automatic cleaning.