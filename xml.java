package com.example.ckyc;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import java.io.StringReader;
import java.io.StringWriter;

public class XmlHelper {
    public static <T> String marshal(T obj, Class<T> type) throws Exception {
            JAXBContext ctx = JAXBContext.newInstance(type);
                    Marshaller m = ctx.createMarshaller();
                            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                                    StringWriter sw = new StringWriter();
                                            m.marshal(obj, sw);
                                                    return sw.toString();
                                                        }
                                                            @SuppressWarnings("unchecked")
                                                                public static <T> T unmarshal(String xml, Class<T> type) throws Exception {
                                                                        JAXBContext ctx = JAXBContext.newInstance(type);
                                                                                Unmarshaller u = ctx.createUnmarshaller();
                                                                                        return (T) u.unmarshal(new StringReader(xml));
                                                                                            }
                                                                                            }