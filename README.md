
# [![**Brooklyn**](https://brooklyn.apache.org/style/img/apache-brooklyn-logo-244px-wide.png)](http://brooklyn.apache.org/)

### Library of Entities for Apache Brooklyn

This sub-project contains various entities not *needed* for Brooklyn,
but useful as building blocks, including entities for webapps,
datastores, and more.

### Building the project

Two methods are available to build this project: within a docker container or directly with maven.

#### Using maven

Simply run:

```bash
mvn clean install
```

#### Using docker

The project comes with a `Dockerfile` that contains everything you need to build this project.
First, build the docker image:

```bash
docker build -t brooklyn:library .
```

Then run the build:

```bash
docker run -i --rm --name brooklyn-library -u $(id -u):$(id -g) \
     --mount type=bind,source="${HOME}/.m2/settings.xml",target=/var/maven/.m2/settings.xml,readonly \
     -v ${PWD}:/usr/build -w /usr/build \
     brooklyn:library mvn clean install -Duser.home=/var/maven -Duser.name=$(id -un)

```

You can speed this up by using your local .m2 cache:
```
docker run -i --rm --name brooklyn-library -u $(id -u):$(id -g) \
    -v ${HOME}/.m2:/var/maven/.m2 \
    -v ${PWD}:/usr/build -w /usr/build \
    brooklyn:library mvn clean install -Duser.home=/var/maven -Duser.name=$(id -un)
```

