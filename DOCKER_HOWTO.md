# Running as a Docker Container

Before pulling the docker image from docker hub, be sure to have ready your
configuration files in a local folder. The files inside the docker image are
not easily editable and can be lost after a container restart.

It's also a very good idea to have a separate folder for the output.

A standard folder structure would be:

```
rsam-ssam
├── conf
│   ├── config.properties
│   └── queries.json
└── output
```
Templates for these files can be found [here](https://github.com/telecoder/rsam-ssam/tree/master/src/main/resources/conf).

* Pull the docker image with:
```
docker pull telecoder/rsam-ssam
```
* Run a docker container using your own `conf` and `output` foulders as mount 
  points for `/rsam-ssam/conf`  and `/rsam-ssam/output` respectively:
```
docker run -v=YOUR_CONF_FOLDER:/rsam-ssam/conf -v=YOUR_OUTPUT_FOLDER:/rsam-ssam/output -p YOUR_PORT:19090 -d telecoder/rsam-ssam
```

It's also possible to mount the logs folder in order to check them on the local
host, for this just add `-v=YOUR_LOGS_FOLDER:/rsam-ssam/logs` to the previous command.


