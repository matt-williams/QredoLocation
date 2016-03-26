# QredoLocation

Share your location, but only with your trusted contacts.

Bump your phones together and establish trust via NFC, and then see each other's location update in realtime, even when the app isn't running.

This was built on top of [Qredo](http://qredo.com/), which provides
*   an encrypted store (Vault API) - used to store your contacts' information
*   end-to-end communication (Rendezvous and Conversation APIs) - used to communicate location information while preventing eaves-dropping.

# Building

To build, you must

*   obtain the qredo-android-sdk-0.8.jar from [Qredo](http://qredo.com/) and put it in `app/libs`
*   create an `app/src/main/res/values/secrets.xml` file that defines the `app_secret` String property.

Then, use Android Studio or Gradle.

# Qredo Hackathon

This was an entry for the Qredo Hackathon, held at CodeNode in London on 5-6 March 2016, and placed joint first - see coverage parts [1](http://blog.qredo.com/post/102d7jg/the-qredo-hackathon-day-1-its-all-about-the-code) and [2](http://blog.qredo.com/post/102d7kx/the-qredo-hackathon-day-2-judgement-day).
