package ru.inversion.meta.impl;

import ru.inversion.meta.IEntityMetaData;
import ru.inversion.meta.IEntityProperty;
import ru.inversion.utils.Checks;
import ru.inversion.utils.IJsonMapper;
import ru.inversion.utils.IJsonable;
import ru.inversion.utils.Tags;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metadata-based JSON support for entity classes.
 */
final class EntityJsonSupport<P> implements IJsonMapper<P> {

   private final IEntityMetaData<P> entityMetaData;

   EntityJsonSupport(IEntityMetaData<P> entityMetaData) {
      this.entityMetaData = Objects.requireNonNull(entityMetaData, "'entityMetaData' is null");
   }

   @Override
   public void toJson( P p, Writer w ) {

      Checks.Require.object( w, "writer" );

      if( p == null )
          return;

      if (p instanceof IJsonable) {
         ((IJsonable) p).toJson(w);
         return;
      }

      try( JsonWriter writer = Json.createWriter(w) ) {
            writer.writeObject(toJsonObject(p));
      }
   }


   /** */
   @Override
   @SuppressWarnings("unchecked")
   public <J> J toJsonO( P p )
   {
      if( p == null )
          return null;

      if( p instanceof IJsonable )
          return ((IJsonable) p).toJsonO();

      return (J) toJsonObject(p);
   }


   /** */
   @Override
   @SuppressWarnings("unchecked")
   public <B> void toJsonB(P p, B b)
   {
      Checks.Require.object( b, "builder" );

      if( p == null )
          return;

      if( p instanceof IJsonable ) {
         ((IJsonable) p).toJsonB(b);
         return;
      }

      if( b instanceof JsonObjectBuilder ) {
         fillJsonObjectBuilder(p, (JsonObjectBuilder) b);
         return;
      }

      throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "Unsupported JSON object builder type: " + b.getClass().getName() );
   }


   /** */
   @Override
   public void toJsonArray( Iterable<P> pl, Writer w )
   {
      Checks.Require.object( w, "writer" );

      try( JsonWriter writer = Json.createWriter(w) ) {
         writer.writeArray(toJsonArrayObject(pl));
      }
   }


   /** */
   @Override
   @SuppressWarnings("unchecked")
   public <A> A toJsonArrayO(Iterable<P> pl) {
      return (A) toJsonArrayObject(pl);
   }


   /** */
   @Override
   public <C> void toJsonArrayB(Iterable<P> pl, C b)
   {
      Checks.Require.object( b, "builder" );

      if( !(b instanceof JsonArrayBuilder) )
          throw new UnsupportedOperationException( Tags.PRODUCT_LABEL + "Unsupported JSON array builder type: " + b.getClass().getName());

      fillJsonArrayBuilder(pl, (JsonArrayBuilder) b);
   }


   /** */
   @Override
   public P fromJson( String jsonStr )
   {
      try {

         Checks.Require.text( jsonStr, "jsonStr" );

         try( JsonReader reader = Json.createReader(new StringReader(jsonStr)) ) {
              return fromJsonObject(reader.readObject());
         }
      }
      catch (Throwable th) {
         throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on make entity '" + entityMetaData.getEntityClass().getName() + "' from json string", th );
      }
   }


   /** */
   @Override
   public List<P> fromJsonArray( String jsonStr ) {

      try {

         Checks.Require.text( jsonStr, "jsonStr" );

         final JsonArray array;

         try( JsonReader reader = Json.createReader(new StringReader(jsonStr))) {
              array = reader.readArray();
         }

         final List<P> retList = new ArrayList<>(array.size());

         for (JsonValue value : array)
         {
            if( value == JsonValue.NULL ) {
               retList.add(null);
               continue;
            }

            if( value.getValueType() != JsonValue.ValueType.OBJECT )
                throw new IllegalArgumentException( Tags.PRODUCT_LABEL + "Expected JSON object in entity array '" + entityMetaData.getEntityClass().getName() + "', but got " + value.getValueType() );

            retList.add( fromJsonObject((JsonObject) value) );
         }

         return retList;
      }
      catch (Throwable th) {
         throw new RuntimeException( Tags.PRODUCT_LABEL + "Error on make entity array '" + entityMetaData.getEntityClass().getName() + "' from json string", th);
      }
   }

   /**
    * Metadata-driven JsonObject.
    * Does not delegate to IJsonable.
    */
   JsonObject toJsonObject(P p)
   {
      JsonObjectBuilder builder = Json.createObjectBuilder();

      if( p != null )
         fillJsonObjectBuilder( p, builder);

      return builder.build();
   }

   /**
    * Metadata-driven JsonArray.
    * Does not delegate item-level serialization to IJsonable.
    */
   JsonArray toJsonArrayObject(Iterable<P> pl)
   {
      JsonArrayBuilder builder = Json.createArrayBuilder();

      fillJsonArrayBuilder(pl, builder);

      return builder.build();
   }

   /** */
   private void fillJsonArrayBuilder( Iterable<P> pl, JsonArrayBuilder builder )
   {
      if( pl == null )
          return;

      for( P p : pl )
      {
         if( p == null )
             builder.addNull();
         else
             builder.add( toJsonObject(p) );
      }
   }

   /** */
   private void fillJsonObjectBuilder( P p, JsonObjectBuilder builder )
   {
      for( IEntityProperty<P, ?> ep : entityMetaData.getPropertiesMap().values() )
      {
         String key   = ep.getPropertyName();
         Object value = ep.invokeGetter(p);

         addJsonValue( builder, key, value );
      }
   }


   /** */
   private void addJsonValue( JsonObjectBuilder builder, String key, Object value )
   {
      if (value == null)
         builder.addNull(key);
      else
      {
         /*
          * values are serialized as strings and interpreted later through metadata.
          */
         builder.add( key, value.toString() );
      }
   }


   /** */
   private P fromJsonObject( JsonObject jsonObject )
   {
      P pojo = entityMetaData.newInstance();

      for (IEntityProperty<P, ?> ep : entityMetaData.getPropertiesMap().values())
      {
         if( ep.isReadOnly() )
             continue;


         String      key = ep.getPropertyName();
         JsonValue value = jsonObject.get(key);

         if( value == null )
             continue;

         if( value == JsonValue.NULL ) {
            ep.invokeSetter(pojo, null);
            continue;
         }

         switch (value.getValueType()) {
            case TRUE:
            case FALSE:
               ep.invokeSetter(pojo, jsonObject.getBoolean(key));
               break;
            case STRING:
               ep.invokeSetter(pojo, jsonObject.getString(key));
               break;
            case NUMBER:
               JsonNumber number = jsonObject.getJsonNumber(key);
               Object numericValue = number.isIntegral() ? number.longValue() : number.bigDecimalValue();
               ep.invokeSetter(pojo, numericValue);
               break;

            default:
               break;
         }
      }
      return pojo;
   }
}