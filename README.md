analytics-android
=================

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.segment.analytics.android/analytics/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.segment.analytics.android/analytics)
[![Javadocs](http://javadoc-badge.appspot.com/com.segment.analytics.android/analytics.svg?label=javadoc)](http://javadoc-badge.appspot.com/com.segment.analytics.android/analytics)

analytics-android is an Android client for [Metarouter](https://metarouter.io)


<div align="center">
  <img src="https://user-images.githubusercontent.com/16131737/53615894-00237380-3b95-11e9-946c-5539d9835c64.png"/>
  <p><b><i>You can't fix what you can't measure</i></b></p>
</div>

Analytics helps you measure your users, product, and business. It unlocks insights into your app's funnel, core business metrics, and whether you have product-market fit.

## How to get started
1. **Collect analytics data** from your app(s).
    - The top 200 Segment companies collect data from 5+ source types (web, mobile, server, CRM, etc.).
2. **Send the data to analytics tools** (for example, Google Analytics, Amplitude, Mixpanel).
    - Over 250+ Segment companies send data to eight categories of destinations such as analytics tools, warehouses, email marketing and remarketing systems, session recording, and more.
3. **Explore your data** by creating metrics (for example, new signups, retention cohorts, and revenue generation).
    - The best Segment companies use retention cohorts to measure product market fit. Netflix has 70% paid retention after 12 months, 30% after 7 years.

[Segment](https://segment.com) collects analytics data and allows you to send it to more than 250 apps (such as Google Analytics, Mixpanel, Optimizely, Facebook Ads, Slack, Sentry) just by flipping a switch. You only need one Segment code snippet, and you can turn integrations on and off at will, with no additional code. [Sign up with Segment today](https://app.segment.com/signup).

### Why?
1. **Power all your analytics apps with the same data**. Instead of writing code to integrate all of your tools individually, send data to Segment, once.

2. **Install tracking for the last time**. We're the last integration you'll ever need to write. You only need to instrument Segment once. Reduce all of your tracking code and advertising tags into a single set of API calls.

3. **Send data from anywhere**. Send Segment data from any device, and we'll transform and send it on to any tool.

4. **Query your data in SQL**. Slice, dice, and analyze your data in detail with Segment SQL. We'll transform and load your customer behavioral data directly from your apps into Amazon Redshift, Google BigQuery, or Postgres. Save weeks of engineering time by not having to invent your own data warehouse and ETL pipeline.

    For example, you can capture data on any app:
    ```js
    analytics.track('Order Completed', { price: 99.84 })
    ```
    Then, query the resulting data in SQL:
    ```sql
    select * from app.order_completed
    order by price desc
    ```

### 🚀 Startup Program
<div align="center">
  <a href="https://segment.com/startups"><img src="https://user-images.githubusercontent.com/16131737/53128952-08d3d400-351b-11e9-9730-7da35adda781.png" /></a>
</div>
If you are part of a new startup  (&lt;$5M raised, &lt;2 years since founding), we just launched a new startup program for you. You can get a Segment Team plan  (up to <b>$25,000 value</b> in Segment credits) for free up to 2 years — <a href="https://segment.com/startups/">apply here</a>!

## Documentation

You can find usage documentation at [https://segment.com/docs/sources/mobile/android/](https://segment.com/docs/sources/mobile/android/).

## Snapshots

All changes committed to master are automatically released as snapshots.

To add a snapshot dependency to your builds, make sure you add the snapshot repository so your build system can look up the dependency.

Maven users can add the following to their `pom.xml`:
```
<repository>
    <id>ossrh</id>
    <name>Sonatype Snapshot Repository</name>
    <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
    <releases>
        <enabled>false</enabled>
    </releases>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

Gradle users should declare this in their repositories block:
```
repositories {
    mavenCentral()
    maven { url 'https://oss.sonatype.org/content/repositories/snapshots/' }
}
```
To include snapshot versions in your gradle project, add the following to build.gradle: 

```
compile 'io.astronomer.analytics.android:analytics:4.1.6-SNAPSHOT'
```

# Open-Source at Metarouter

One of our core values at Metarouter is "openness." We see this manifest in our dedication to open-source. We are relentless to **only invent what we must invent**; to use and contribute to the large and growing body of open-source software that countless developers have selflessly contributed to the world.

And it's not just about receiving value. Open-source is a two-way street. A core element of our business strategy is to open-source the [Aries Framework](github.com/aries-data), with the goal to reduce the amount of time developers waste re-writing integrations.

We also have many open-source projects in [our main Github organization](https://github.com/super-collider).

## Special Notice re: Analytics.js and Segment

Early on, in order to get clickstream data collection capability quickly within our platform, and after a conversation with Peter Reinhart (an early Analytics.js contributor and CEO of Segment), we decided to adopt Analytics.js to support it as a standard for clickstream data collection. As Segment has built a successful commercial venture around the core library, they've open-sourced a number of supporting plugins and SDKs that we've also adopted.

Clickstream is one part of Metarouter, and an important one to our early customers. We publicly thank Segment for their open-source contributions, it made it easier to stand up that part of our platform. In appreciation and in the spirit of our cooperation, we open-source all of our Analytics.js-related code, contributing back to a growing ecosystem.

## License

```
WWWWWW||WWWWWW
 W W W||W W W
      ||
    ( OO )__________
     /  |           \
    /o o|    MIT     \
    \___/||_||__||_|| *
         || ||  || ||
        _||_|| _||_||
       (__|__|(__|__|

The MIT License (MIT)

Copyright (c) 2016 Segment, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
