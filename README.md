# LensKit Android App's recommender

This project is prepared to run a Item-Item Colaborative Filtering algorithm querying
by item instead of user. To process extensive datasets several objects had to
be serialized so memory is not an issue. To speed up the training and the testing parallelization of Item-Item has been done so it can take the most out of the hardware.

The algorithm configuration is at the basket.groovy file, where we set every parameter
required to run.

The [LensKit home page][LensKit] has further documentation for LensKit, as well as
links to their bug tracker and wiki.

## Project Setup

This project uses [Gradle][gradle] for build and dependency management. It is
easy to import into an IDE; Gradle support is included with or available for
NetBeans, IntelliJ IDEA, and Eclipse.  These IDEs will import your project directly
from the Gradle `build.gradle` file and set up the build and dependencies.

The `build.gradle` file contains the project definition and its dependencies. Review
this for how we pull in LensKit, and how to depend on other modules.

## Building and Running

In the Gradle build, we use the Application plugin to create a shell script and copy
the dependency JARs in order to run the LensKit application.

This project runs on a copy of the Applications data set, included in the `data` directory.
This data is unary and is split in two different files: 
    appNames.csv (contains app names and app id's)
    exploded.csv (each line contains user:item:value:timestamp).
To change the dataset one has to change the .yml file wich.

You can run lenskit-hello through your IDE, or from the command line
as follows:

    $ ./gradlew build
    $ ./build/install/lenskit-aptoide/bin/lenskit-aptoide [FLAG] [CONFIG file]
    
If you are on Windows, do:

    C:\LensKit\lenskit-hello> .\gradlew.bat build
    C:\LensKit\lenskit-hello> .\build\install\lenskit-aptoide\bin\lenskit-aptoide.bat [FLAG] [CONFIG file]
    

### Arguments

We can run the project either on Train mode or Test mode just by changing the [FLAG].
The Train mode provides a compressed model for later use.
The Test mode receives a compressed model and returns recommendations.

At the [CONFIG file] is where we settle things like dataset path and compressed model path.
Right now the file must have in the following order:
    1 - Trained Model file (either for input and output);
    2 - Configuration file for training;
    3 - Data file (.yml required);
    4 - Test input file;
    5 - Test output file;
    6 - Log File;
    7 - Number of recommendations needed per item;
    8 - Number of Threads for testing;

Have fun!

[LensKit]: http://lenskit.org
[gradle]: http://gradle.org
