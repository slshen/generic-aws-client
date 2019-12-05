# generic-aws-client

[![Build Status](https://travis-ci.com/slshen/generic-aws-client.svg?branch=master)](https://travis-ci.com/slshen/generic-aws-client)

Here lies a proof-of-concept generic AWS client.  "Generic" in the sense that
it doesn't include or use any strongly typed classes to represent requests or responses.  Instead the client accepts and returns `JsonObject` objects.

Some notes:

* This is somewhat a copy of [boto3](https://github.com/boto/boto3).  (It uses an extremely cut-down version of of boto3's model files.)

* Credentials are levered out of the standard AWS sdk `AWSCredentialsProvider` so nothing special is going on there.

* Request signing is a straightforward implementation cribbed from the AWS documentation.  Here it takes the form of an [oktthp](https://github.com/square/okhttp/) interceptor.
 
* AWS services come in several flavors ("protocols" in boto3 lingo.)  This code handles the JSON variants more or less and makes a half-hearted attempt at the XML ones.  (The [Jackson XMLMapper](https://github.com/FasterXML/jackson-dataformat-xml) doesn't do the right thing out-of-the-box so there's a custom XML to JSON parser that only sort of works.)
