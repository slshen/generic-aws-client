// Copyright 2019 Sam Shen
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.slshen.genaws;

import java.io.IOException;
import java.io.Reader;
import java.util.Deque;
import java.util.LinkedList;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeCreator;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/** Parse an AWS query style XML message into a JSON tree. */
class AwsXmlParser {

  private XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

  private static class Frame {
    Frame(String elementName) {
      this.elementName = elementName;
    }

    final String elementName;
    JsonNode content = MissingNode.getInstance();
  }

  public JsonNode parse(JsonNodeCreator creator, XMLStreamReader reader) throws XMLStreamException {
    Deque<Frame> stack = new LinkedList<>();
    stack.push(new Frame(null));
    while (reader.hasNext()) {
      int e = reader.next();
      switch (e) {
        case XMLStreamReader.START_ELEMENT:
          {
            Frame top = stack.peek();
            if (!top.content.isObject()) {
              top.content = creator.objectNode();
            }
            stack.push(new Frame(reader.getLocalName()));
          }
          break;
        case XMLStreamReader.CHARACTERS:
          {
            Frame frame = stack.peek();
            if (frame.content.isMissingNode()) {
              frame.content = new TextNode(reader.getText());
            }
          }
          break;
        case XMLStreamReader.END_ELEMENT:
          {
            Frame frame = stack.pop();
            if (frame.content.isObject() && frame.content.size() == 1) {
              /*
               * If this frame contains a single object whose value is a list then
               * eliminate the object, e.g. transform "tagSet": { "item": [ ... ] } to
               * simply "tagSet": [ ... ]
               */
              JsonNode singleValue = frame.content.fields().next().getValue();
              if (singleValue.isArray()) {
                frame.content = singleValue;
              }
            }
            Frame top = stack.peek();
            if (top.elementName == null) {
              /*
               * Don't include an extra object for root element
               */
              top.content = frame.content;
            } else {

              JsonNode container = top.content.path(frame.elementName);
              if (container.isMissingNode() || (container.isObject() && container.size() == 0)) {
                ((ObjectNode) top.content).set(frame.elementName, frame.content);
              } else {
                /*
                 * We're only going to put stuff in an array if there's more than
                 * one thing
                 */
                if (!container.isArray()) {
                  container = ((ObjectNode) top.content).putArray(frame.elementName).add(container);
                }
                ((ArrayNode) container).add(frame.content);
              }
            }
          }
          break;
      }
    }
    return stack.pop().content;
  }

  public JsonNode parse(JsonNodeCreator nodeCreator, Reader reader) throws IOException {
    try {
      return parse(nodeCreator, xmlInputFactory.createXMLStreamReader(reader));
    } catch (XMLStreamException e) {
      throw new IOException("could not parse XML", e);
    }
  }
}
