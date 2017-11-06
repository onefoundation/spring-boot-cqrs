package com.onefoundation.cqrsdemo.cart;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.couchbase.client.java.document.RawJsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.query.AsyncN1qlQueryResult;
import com.couchbase.client.java.query.N1qlParams;
import com.couchbase.client.java.query.N1qlQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onefoundation.cqrsdemo.cart.additem.ItemAddedEvent;
import com.onefoundation.cqrsdemo.cart.event.Event;
import com.onefoundation.cqrsdemo.cart.removeitem.ItemUpdatedEvent;
import com.onefoundation.cqrsdemo.db.Couchbase;

@Service
public class CartEventDAO {
	
	ObjectMapper mapper = new ObjectMapper();
	@Autowired
	Couchbase db;
	
	public List<Event> getCartEvents(String cartId) {
		
		N1qlParams params = N1qlParams.build().adhoc(false);
    	JsonObject values = JsonObject.create().put("cartId", cartId);
    	N1qlQuery query = N1qlQuery.parameterized("select default.* from `default` where docType='CartEvent' and cartId=$cartId order by sequenceNumber", values, params);
    	
		List<Event> events = db.getBucket().async().query(query)
         .flatMap(AsyncN1qlQueryResult::rows)
         .map(result -> {
			return createEvent(result.value().toString());
		  })
         .toList()
         .timeout(10, TimeUnit.SECONDS)
         .toBlocking()
         .single();
		 
		return events;
		
	}
	

	public void save(List<Event> events) {
		
		for(Event e: events) {
			String es = null;
			try {
				es = mapper.writeValueAsString(e);
			} catch (JsonProcessingException ex) {
				ex.printStackTrace();
			}
			
			RawJsonDocument jsonDoc = RawJsonDocument.create(e.getEventId(), es);
			db.getBucket().insert(jsonDoc);
			
		}
		
	}
	
	private Event createEvent(String json) {
		Event event = null;
		try {
			if(json.contains("ItemRemovedEvent")) {
				event =  mapper.readValue(json, ItemUpdatedEvent.class);
			} else if (json.contains("ItemAddedEvent")) {
				event =  mapper.readValue(json, ItemAddedEvent.class);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return event;
	}
	
}
