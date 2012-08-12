---
layout: page
title: Xobs' Blog
tagline: Notes on various projects
---
{% include JB/setup %}

Complete usage and documentation available at: [Jekyll Bootstrap](http://jekyllbootstrap.com)

## Post list

Here's a sample "posts list".

<ul class="posts">
  {% for post in site.posts %}
    <li><span>{{ post.date | date_to_string }}</span> &raquo; <a href="{{ BASE_PATH }}{{ post.url }}">{{ post.title }}</a></li>
  {% endfor %}
</ul>

