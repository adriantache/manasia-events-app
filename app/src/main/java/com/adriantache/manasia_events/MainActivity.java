package com.adriantache.manasia_events;

import android.app.ActivityOptions;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.adriantache.manasia_events.adapter.EventAdapter;
import com.adriantache.manasia_events.custom_class.Event;
import com.adriantache.manasia_events.db.DBUtils;
import com.adriantache.manasia_events.loader.EventLoader;
import com.adriantache.manasia_events.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.adriantache.manasia_events.EventDetail.NOTIFY;
import static com.adriantache.manasia_events.EventDetail.SHARED_PREFERENCES_TAG;
import static com.adriantache.manasia_events.db.EventContract.CONTENT_URI;
import static com.adriantache.manasia_events.db.EventContract.EventEntry.COLUMN_EVENT_CATEGORY_IMAGE;
import static com.adriantache.manasia_events.db.EventContract.EventEntry.COLUMN_EVENT_DATE;
import static com.adriantache.manasia_events.db.EventContract.EventEntry.COLUMN_EVENT_DESCRIPTION;
import static com.adriantache.manasia_events.db.EventContract.EventEntry.COLUMN_EVENT_NOTIFY;
import static com.adriantache.manasia_events.db.EventContract.EventEntry.COLUMN_EVENT_PHOTO_URL;
import static com.adriantache.manasia_events.db.EventContract.EventEntry.COLUMN_EVENT_TITLE;
import static com.adriantache.manasia_events.notification.NotifyUtils.scheduleNotifications;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<List<Event>> {
    public static final String DBEventIDTag = "DBEventID";
    private static final String TAG = "MainActivity";
    public ArrayList<Event> events;
    @BindView(R.id.list_view)
    ListView listView;
    @BindView(R.id.music_toggle)
    Button music_toggle;
    @BindView(R.id.shop_toggle)
    Button shop_toggle;
    @BindView(R.id.hub_toggle)
    Button hub_toggle;
    @BindView(R.id.logo)
    ImageView logo;
    @BindView(R.id.busy_level)
    TextView busy_level;
    @BindView(R.id.filters)
    TextView filters;
    private String REMOTE_URL;
    private boolean music;
    private boolean shop;
    private boolean hub;
    private boolean notifyOnAllEvents;
    private boolean layout_animated = false;

    //todo test if necessary after using TaskStackBuilder
    //closes app on back pressed to prevent infinite loop due to how the stack is built coming from a notification
    @Override
    public void onBackPressed() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
    }

    //update list from db when returning from EventDetail
    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        //retrieve SharedPrefs before binding the ArrayAdapter
        getPreferences();
        //add visual indicators that filters are set
        checkFiltersSet();

        //get remote URL or use local data
        REMOTE_URL = getRemoteURL();

        //populate the global ArrayList of events
        //todo decide if filter makes sense, currently keeping it to simplify transition to EventDetail activity
        updateDatabase(!TextUtils.isEmpty(REMOTE_URL));
        events = (ArrayList<Event>) DBUtils.readDatabase(this);

        if (events != null) {
            populateListView();
        }

        //code to minimize and maximize logo on click (maybe not terribly useful, but it looks neat)
        //todo modify code to show some useful info instead of just minimizing logo (figure out available size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            logo.setOnClickListener(v -> minimizeLogo());
        }

        //todo figure out how to fetch this (ideally same place we store the JSON or database)
        updateBusyLevel();
    }

    private void populateListView() {
        //populate list
        //todo set empty list text view and progress bar
        listView.setAdapter(new EventAdapter(this, events));

        //set click listener and transition animation
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Event event = (Event) parent.getItemAtPosition(position);

            Intent intent = new Intent(getApplicationContext(), EventDetail.class);
            intent.putExtra(DBEventIDTag, event.getDatabaseID());

            //code to animate event details between activities
            ActivityOptions options = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Utils.compareDateToToday(event.getDate()) < 0)
                    options = ActivityOptions
                            .makeSceneTransitionAnimation(this,
                                    Pair.create(view.findViewById(R.id.thumbnail), "thumbnail"),
                                    Pair.create(view.findViewById(R.id.category_image), "category_image")
                            );
                else
                    options = ActivityOptions
                            .makeSceneTransitionAnimation(this,
                                    Pair.create(view.findViewById(R.id.thumbnail), "thumbnail"),
                                    Pair.create(view.findViewById(R.id.notify_status), "notify_status"),
                                    Pair.create(view.findViewById(R.id.category_image), "category_image")
                            );
            }

            //if we can animate, do that, otherwise just open the EventDetail activity
            if (options != null) {
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
        });
    }

    private String getRemoteURL() {
        String remoteURL = null;

        //get API key from file
        try {
            if (Arrays.asList(getResources().getAssets().list("")).contains("dataURL.txt")) {
                AssetManager am = getApplicationContext().getAssets();
                InputStream inputStream = null;
                try {
                    inputStream = am.open("dataURL.txt");
                } catch (IOException e) {
                    Log.e(TAG, "Cannot read API key from file.", e);
                }

                if (inputStream != null) {
                    int ch;
                    StringBuilder sb = new StringBuilder();
                    try {
                        while ((ch = inputStream.read()) != -1) {
                            sb.append((char) ch);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Cannot read API key InputStream.", e);
                    }

                    if (sb.length() != 0) remoteURL = sb.toString();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot open API key file.", e);
        }

        return remoteURL;
    }

    /**
     * This method does three things:
     * 1. Fetches an ArrayList of data from a remote source (todo - using dummyData for now)
     * 2. Updates the local SQLite database with new events or updates to existing events
     * 3. Synchronizes ListView positions with SQLite IDs (todo - rewrite this or ignore it, IDs are stored in each Event object)
     * todo add logic to delete entries deleted from remote
     *
     * @param getRemote get remote data to store in the DB (todo - does this make any sense if we update only in EventDetail?)
     */
    //todo improve duplicate identification code OR keep code to just delete database contents (but add check for remote events FIRST)
    private void updateDatabase(boolean getRemote) {
        if (!getRemote) {
            inputRemoteEventsIntoDatabase((ArrayList<Event>) dummyData());
        } else
            getSupportLoaderManager().initLoader(1, null, this).forceLoad();
    }

    private void inputRemoteEventsIntoDatabase(ArrayList<Event> remoteEvents) {
        if (remoteEvents != null) {
            //first of all transfer all notify statuses from the local database to the temporary remote database
            ArrayList<Event> DBEvents = (ArrayList<Event>) DBUtils.readDatabase(this);
            remoteEvents = Utils.updateNotifyInRemote(remoteEvents, DBEvents);

            //then delete ALL events from the local table1
            getContentResolver().delete(CONTENT_URI, null, null);

            //then add the remote events to the local database
            for (Event event : remoteEvents) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_EVENT_TITLE, event.getTitle());
                values.put(COLUMN_EVENT_DESCRIPTION, event.getDescription());
                values.put(COLUMN_EVENT_DATE, event.getDate());
                if (!TextUtils.isEmpty(event.getPhotoUrl()))
                    values.put(COLUMN_EVENT_PHOTO_URL, event.getPhotoUrl());
                values.put(COLUMN_EVENT_CATEGORY_IMAGE, event.getCategory_image());
                values.put(COLUMN_EVENT_NOTIFY, event.getNotify());

                getContentResolver().insert(CONTENT_URI, values);
            }

            //update event notifications for all future events fetched from the remote database
            if (notifyOnAllEvents) scheduleNotifications(this, true);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void minimizeLogo() {
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(getApplicationContext(), R.layout.activity_main_animate);
        ConstraintSet initialConstraintSet = new ConstraintSet();
        initialConstraintSet.clone(getApplicationContext(), R.layout.activity_main);
        ConstraintLayout mConstraintLayout = findViewById(R.id.constraint_layout);
        TransitionManager.beginDelayedTransition(mConstraintLayout);
        if (!layout_animated) {
            constraintSet.applyTo(mConstraintLayout);
            layout_animated = true;
        } else {
            initialConstraintSet.applyTo(mConstraintLayout);
            layout_animated = false;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void updateBusyLevel() {
        //todo implement actual code
        String busyLevel = "Prietenos";
        busy_level.setText(busyLevel);
        switch (busyLevel) {
            case "Lejer":
                busy_level.setTextColor(0xff2196F3);
                break;
            case "Prietenos":
                busy_level.setTextColor(0xff4CAF50);
                break;
            case "Optim":
                busy_level.setTextColor(0xffE91E63);
                break;
            case "Full":
                busy_level.setTextColor(0xfff44336);
                break;
            default:
                busy_level.setTextColor(0xff000000);
                break;
        }
    }

    //todo enable functionality to toggle all notifications
    private void getPreferences() {
        SharedPreferences sharedPrefs = this.getSharedPreferences(SHARED_PREFERENCES_TAG, Context.MODE_PRIVATE);
        music = sharedPrefs.getBoolean("music", true);
        shop = sharedPrefs.getBoolean("shop", true);
        hub = sharedPrefs.getBoolean("hub", true);
        notifyOnAllEvents = sharedPrefs.getBoolean(NOTIFY, false);

        setFilterColor();
    }

    private void setPreferences() {
        SharedPreferences sharedPref = this.getSharedPreferences(SHARED_PREFERENCES_TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("music", music);
        editor.putBoolean("shop", shop);
        editor.putBoolean("hub", hub);
        editor.apply();
    }

    //create dummy data objects to populate the list
    //todo replace dummy data with real data, eventually
    //todo implement SwipeRefreshLayout (is it really needed?)
    private List<Event> dummyData() {
        ArrayList<Event> arrayList = new ArrayList<>();

        arrayList.add(new Event("06.04.2018",
                "Glittoris la Linia 1",
                "Visul cel mai mare al Dianei (de om care pune muzică, gen) e ca doi oameni între care există o tensiune sexuală să ajungă să se pupe pentru prima oară la petrecerea ei. \uD83D\uDC49\uD83D\uDC4C\uD83D\uDC8D\n" +
                        "Să știe că Diana a fost pețitoare. Se dansează cu plante și femei pe pe rnb, tehno și brockhampton. \uD83C\uDF31\uD83D\uDCBD\n" +
                        "De Linia 1 nu mai zicem că știți deja. ➖\n" +
                        "\n" +
                        "https://www.youtube.com/c/Linia1prezinta\n" +
                        "https://www.facebook.com/manasiahub\n" +
                        "\n" +
                        "Vineri 6 Aprilie\n" +
                        "Manasia Hub (Str. Stelea Spatarul nr. 13)\n" +
                        "\n" +
                        "Intrare libera.",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/29573169_343846172791962_1467685779540178961_n.jpg?_nc_cat=0&oh=59195fcc6236cec1ff599860b64518ac&oe=5B9457A3",
                R.drawable.music));
        arrayList.add(new Event("13.04.2018",
                "Mălina & Tugay și Tugay & Mălina la Linia 1",
                "Malina si Tugay – combinatie de Du Bye Bye\n" +
                        "Malina e un mare fan al imperativului categoric si al fetelor cu par la subrat. Ii place Sylvia Plath si cateodata o citeste cu manele pe fundal. \uD83D\uDC85\uD83D\uDCDA⚔️\n" +
                        "Tugay e proud member of Alex si Tugay & Tugay si Alex si zice ca l-a calcat masina dar a reinviat, s-a lasat de CocaCola si vine sa puna muzica.\n" +
                        "Amandoi la Linia 1 \uD83C\uDFB0\uD83D\uDCBD\uD83D\uDC23\n" +
                        "\n" +
                        "\n" +
                        "Vineri 13 aprilie Manasia Hub\n" +
                        "\n" +
                        "Intrare libera.",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/30442737_346746755835237_3773800850313445376_n.jpg?_nc_cat=0&oh=0a60fe77a48085f0cd13d07c11ea3888&oe=5B93C571",
                R.drawable.music));
        arrayList.add(new Event("14.04.2018",
                "Dirty Disco pres. Eugen Radescu and Wefa at Manasia Hub",
                "Dirty Disco w/ Eugen Rădescu & Wefa\n" +
                        "\n" +
                        "A deliberate attempt to get your body moving while losing yourself in the garden, on the dancefloor or ... everywhere in between. \n" +
                        "\n" +
                        "A mash-up of disco-pop euphoria, oriental extravaganza with a pinch of Romanian manele for a decadent after taste. \n" +
                        "\n" +
                        "One of those mornings that have a sweet flavour of '''i don't remember much, bur I know I had it all. ''\n" +
                        ".\n" +
                        "Manasia Hub | real people | real stories \n" +
                        "Stelea Spataru 13",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/29793163_2129934620573343_3532333566979473408_n.jpg?_nc_cat=0&oh=da3d50362ed5e55f097133e727abe502&oe=5B67A779",
                R.drawable.music));
        arrayList.add(new Event("20.04.2018",
                "Discotek Bash w/ Iancu & Groovemanescu",
                "Discotek Bash w/ Iancu & Groovemanescu\n" +
                        "\n" +
                        "In need of that familiar feeling for a FRI night out? \n" +
                        "\n" +
                        "Something of a house party that actually takes over a whole garden filled up with friends? :D \n" +
                        "\n" +
                        "Well ... you got it ... our dear friends and colleagues are taking over the DJ desk for a proper DiscoTech bash!\n" +
                        "\n" +
                        "Expect an immersive house sound where bits and pieces of tech groove and disco memorabilia are woven together just in time for the perfect tequila sunrise ;) \n" +
                        "\n" +
                        "Manasia Hub | real people | real stories \n" +
                        "Stelea Spataru 13",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/30739942_2136764383223700_5742396593584209920_n.jpg?_nc_cat=0&oh=1653c8e3bff2cfd90dd7c0517d9c2f41&oe=5B55BCA8",
                R.drawable.music));
        arrayList.add(new Event("21.04.2018",
                "Record Store Day by MadPiano",
                "The best day of shopping for audiophiles and music-lovers, \n" +
                        "Record Store Day by MadPiano is back for 2018.\n" +
                        "This year marking the 11th anniversary of Record Store Day around the world, an awareness-raising celebration of the brick-and-mortar independent record store as a cultural hub for hardcore vinyl geeks and casual music fans alike.\n" +
                        "\n" +
                        "Line-up:\n" +
                        "14:00 >> Lazar Cristian\n" +
                        "16:00 >> Rareş Gall (Gotgroove?)\n" +
                        "18:00 >> Posh/posh111\n" +
                        "20:00 >> Live Ambient Guitar Session w/ Leonte George\n" +
                        "\n" +
                        "*OFF 10% for all vinyl\n" +
                        "*Tombola with vinyls for all buyers on this day\n" +
                        "*In addition we are offering exclusive releases\n" +
                        "\n" +
                        "Get social:\n" +
                        "MadPiano Record Store\n" +
                        ">> http://madpiano.ro/\n" +
                        ">> https://soundcloud.com/madpiano_ro\n" +
                        ">> https://goo.gl/rkrVNa\n" +
                        "Manasia Hub\n" +
                        "Stelea Spătarul 13, Bucharest, Romania, 030211",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/30729359_1138878096253651_5540754426124626056_n.jpg?_nc_cat=0&oh=bd41574512f8ab8870a20e447641935a&oe=5B9947FB",
                R.drawable.shop));
        arrayList.add(new Event("21.04.2018",
                "Record Shop Day After Party w/ The Groovers Delight",
                "High time to dig into the vinyl sound people ! ♥ \n" +
                        "From dusk till down, from garden to vinyl shop and beyond :D, " +
                        "we're delighted to celebrate the authentic vinyl sound with the " +
                        "right spin and a twist: a VINYL BACK TO BACK DJ set by The Groovers Delight. \n" +
                        "Gifted selectors, Vlad Oscar & Cipri M Marc have been digging for " +
                        "some proper funk & disco groove that promises to turn the night into " +
                        "a 'let your body take control' kinda' thing.\n" + "\n" +
                        "Record Store Day by MadPiano AFTER PARTY | 23:00 \n" +
                        "Manasia Hub | real stories | real people |\n" +
                        "Stelea Spataru 13",
                "https://i.imgur.com/v2OBKYS.jpg",
                R.drawable.music));
        arrayList.add(new Event("22.04.2018",
                "Pre-Owned Market",
                "• PRE-OWNED MARKET\n" +
                        "• 22 aprilie | 12:00 pm | Manasia Hub | \n" +
                        "• Diggers | Shopping | Music | No Bad Vibes!\n" +
                        "_______________________________________________\n" +
                        "\n" +
                        "Facem o mare nebunie de Pre-Owned Market, duminică, 22 aprilie la hubul Manasia, începand cu 12:00pm. \n" +
                        "Te ajutăm să te detașezi de niște lucruri mișto, pe care alții le-ar găsi și mai mișto, chiar folosindu-le cu drag!\n" +
                        "\n" +
                        "Uite care e combinația: fie că e vorba de cadourile pe care le-ai primit și de care nu te-ai mai atins, de goblenurile de la bunica, hainele care încă stau cu etichetă în dulap sau sneakerșii de pe Ebay așteptați 3 saptămâni, doar ca să constați ca erau mici, adu-le pe toate la Pre-Owned Market.\n" +
                        "\n" +
                        "Uite așa, ți-ai scos banii de 1 mai! \n" +
                        "Cu ocazia asta o pui și de un outfit nou de festival. Oricum, știi și tu că te-a mai văzut lumea cu rochia aia, așa că... dă-o mai departe! \n" +
                        "\n" +
                        "Hai să faci afacere și să îți iei un Rembrandt la colecție, gantere de 10 kile, vinyl cu Jackson sau Eminem și cine știe ce alte haine îți vor face cu ochiul.\n" +
                        "\n" +
                        "Vinde, cumpără, combină... pe muzică fină, în curte la cald, totul e frumos! \n" +
                        "\n" +
                        "• Dj set : Olaru \uD83C\uDF9B️ https://soundcloud.com/olarugeorge\n" +
                        "Vinyl selections by Madpiano \uD83C\uDFB6 https://www.facebook.com/madpiano.ro\n" +
                        "• Special drinks & food available \n" +
                        "_______________________________________________\n" +
                        "\n" +
                        "Locuri limitate! Pentru înscriere PM sau mail pe bucharestinart@gmail.com și îți vom trimite cele necesare.\n" +
                        "| no junk please, photo selection will be made\n" +
                        "| nu aduce nimic din ce nici tu nu ai cumpăra \n" +
                        "_______________________________________________\n" +
                        "\n" +
                        "Intrare libera pentru vizitatori.\n" +
                        "Eveniment susținut de ▲ BUCHAREST IN ART ▲", "https://i.imgur.com/8TIPwNB.png",
                R.drawable.shop));
        arrayList.add(new Event("27.04.2018",
                "Lansare videoclip Baieti Cuminti la Linia 1",
                "Băieți Cuminți are back si lanseaza clip la \"Valuri\", Linia 1, ofc, ca au venit si banii de pe iutub. \uD83C\uDF0A\uD83C\uDF7E \n" +
                        "E un clip misto, asa, de vacanta \uD83C\uDF34\n" +
                        "E gen bring your own popcorn,il proiectam, dupa care facem party, tot la Manasia Hub \uD83C\uDF5A\uD83E\uDD62\n" +
                        "\n" +
                        "Vineri 27 aprilie, Stelea Spatarul nr. 13.\n" +
                        "\n" +
                        "Intrare libera.",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/31059487_351218945388018_5238109661428711424_n.jpg?_nc_cat=0&oh=08c060473048a2661fff3575f42e7b5d&oe=5B6939B5",
                R.drawable.music));
        arrayList.add(new Event("01.05.2018",
                "1 mai de Manasia",
                "De 1 mai nu plecam niciunde! Si pana atunci ... tot aici ne gasiti ... terasa e deschisa daily .. asa ca nu va sfiiti! ;) \n" +
                        "\n" +
                        "Ramenem pe baricade ... bricolam de zor si ne anturam la un mic,o bere ... mustard on top! ;)\n" +
                        "\n" +
                        "Sa nu uitam de DJ set ca fara el ... ne ia valu cam prost! :))\n" +
                        "\n" +
                        "1 mai de Manasia 12 PM - 01 AM \n" +
                        "\n" +
                        "Manasia HUB\n" +
                        "real people | real stories \n" +
                        "Stelea Spataru 13",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/31392196_2140768786156593_514869821767155712_n.jpg?_nc_cat=0&oh=109c3918dd547f72fba2822f17b4b020&oe=5B962E23",
                R.drawable.hub));
        arrayList.add(new Event("06.05.2018",
                "Manasia Food opening event | brunching in the garden w/ KOSTA",
                "Manasia food Opening Event | Brunching in the garden with KOSTA \n" +
                        "starting 13:00 – ending at the last bite | wine & food tasting 13-18\n" +
                        "\n" +
                        "Around here … we like it real \n" +
                        "We’ve imagined a space where collaboration and creative energy are the starters of a beautiful friendship.\n" +
                        "We’ve re-imagined what a police station looks like and feels like, giving the 1930 building a whole new purpose: creative office spaces. \n" +
                        "As you can see … we’ve always gathered like minded people around as we appreciate the realness of it all. . \n" +
                        "\n" +
                        "And NOW, as real as it gets, we felt the need to add an EXTRA layer of emotion to our creative HUB. \n" +
                        "Manasia HUB proudly introduces you to …\n" +
                        "\n" +
                        "Manasia food \n" +
                        "real people | real stories | REAL FOOD",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/31400920_2140369172863221_6966862658385477632_n.jpg?_nc_cat=0&oh=6630ba23d0f2302c0fb6abb7a9b82386&oe=5B62AE78",
                R.drawable.hub));
        arrayList.add(new Event("12.05.2018",
                "PARADAIZ grătăruts PARTY la Manasia",
                "\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\n" +
                        "PARADAIZ GRĂTĂRUTS PARTY ZI & NOAPTE @ Manasia Hub \n" +
                        "\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\n" +
                        "FETE ȘI BĂIEȚI, PE 12 MAI 2018, DE LA ORA 14.00, PARADAIZ TAPE MAȘINA RULEAZĂ CASETE CU MUZICI DE TERASĂ DIN ANII 90, ÎN TIMP CE FIASTRU RULEAZĂ LA GRĂTĂRUTS MICI - LA PREȚURI IMBATABILE - PENTRU PARTICIPANȚI.\n" +
                        "\n" +
                        "DUPĂ CELEBRA LĂSARE A ÎNTUNERICULUI, PETRECEREA SE MUTĂ ÎNĂUNTRU, PE AFIȘ STÂND BINE MERSI UN LINEUP ASTRAL\n" +
                        "\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\n" +
                        "DOMNU X /// https://www.mixcloud.com/domnux\n" +
                        "GINO GIUVAERUL /// https://soundcloud.com/cristeainstitute\n" +
                        "URMUM goo.gl/2WrKXR\n" +
                        "\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\n" +
                        "DONAȚIE LA INTRARE: 10 LEOPARZI\n" +
                        "\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\uD83D\uDD3A\n" +
                        "Toți banii din donații/intrări și cei din încasările de pe mici pleacă, prin intermediul Claudia Costea/Young Roma Maramureș, către copiii din comunitatea Pirita, aflați la clasele mici ale Liceului Penticostal din Baia Mare. Cu ajutorul acestor bani, ei vor primi un mic-dejun, un gest banal dar foarte eficient în diminuarea ratei abandonului școlar în comunitățile marginalizate.\n" +
                        "\n" +
                        "Povestea copiilor de pe Pirita, aici: http://www.documentaria.ro/content/pirita-mircea-restea",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/31919587_2160987484112671_3996558869558984704_n.jpg?_nc_cat=0&oh=d8337ee3e705bdb4ae34d77367688516&oe=5B4E9D3E",
                R.drawable.music));
        arrayList.add(new Event("19.05.2018",
                "Expoziție \"Pe val\"",
                "“Pe val” este un proiect expozițional gândit și realizat de trei tinere artiste vizuale, sub egida DISC Studio și cu susținerea Manasia Hub. Toți cei care vor trece pragul hubului Manasia în ziua de 19 mai 2018 vor pătrunde într-o lume a peisajelor marine construită atât cu imagini, cât și cu muzica live realizată de Ioana Șelaru, Cristian Galitî, Răzvan Cipcă și Traian Dosoftei. Pești, portrete, valuri și surfing - acestea sunt câteva dintre temele pe care le veți explora în cadrul aceastei expoziții. \n" +
                        "\n" +
                        "Anca Cobzaru este studentă în anul III la secția Grafică a UAD Cluj. Anca iubește pensulele de calitate, să râdă și să fie artistă. \n" +
                        "\n" +
                        "https://www.instagram.com/ancaandr/\n" +
                        "\n" +
                        "Ana Costov este de asemenea studentă în anul III la secția de Grafică a UAD Cluj. Ana iubește călătoriile în Spania, surfiștii și ilustrațiile, având un stil pe care nu îl poți uita prea ușor.\n" +
                        "\n" +
                        "https://www.behance.net/anacostov\n" +
                        "\n" +
                        "https://www.instagram.com/anacosmos/\n" +
                        "\n" +
                        "Irina Șelaru, studentă în anul II la aceeași secție a aceleași universități, iubește mirosul creioanelor proaspăt ascuțite și alege să își petreacă mare parte din timp în atelier.\n" +
                        "\n" +
                        "https://www.behance.net/selaruirin68fe\n" +
                        "\n" +
                        "https://www.instagram.com/selaruirina/\n" +
                        "\n" +
                        "DISC-District of Screen Composers - este o comunitate din domeniul muzical, alcătuită din tineri muzicieni care au ca scop comun crearea, adaptarea și masterizarea unei game complexe de produse audio. Mai exact, compunem coloane sonore originale pentru orice format video, evenimente si prezentări. \n" +
                        "\n" +
                        "https://soundcloud.com/discstudio\n" +
                        "\n" +
                        "https://www.instagram.com/discstudio/\n" +
                        "\n" +
                        "https://www.facebook.com/disc.disctrict/",
                "https://scontent.fotp3-3.fna.fbcdn.net/v/t1.0-9/32130262_196932147697442_7189718203453407232_n.jpg?_nc_cat=0&oh=93e9533f87b8a7e96a9780e33396d3b5&oe=5B859E06",
                R.drawable.hub));
        arrayList.add(new Event("26.05.2018",
                "Search for Sucarov",
                "Sucarov revine la Manasia, de data aceasta ,,cu Noaptea în cap’’( bu-hu-hu), pe zi.\n" +
                        "În funcție de stare, vreme și fizică, Sucarov prezintă bijuterii muzicale din anii 1900-1980.\n" +
                        "Search for Sucarov, curând apare și proiectul personal \uD83C\uDFB6 sub același nume.\n" +
                        "Iubirea noastră-i un kazoo!",
                "https://scontent.fotp3-1.fna.fbcdn.net/v/t1.0-9/32650661_2150225811877557_8249354285406486528_n.jpg?_nc_cat=0&_nc_eui2=AeGTWRiiUSLtLab1hq25iUa0TkJu_xjfnaqEh6QnuyPJCNWFnLOdzN07nF6fTY6vPuenSw9PDgip4k4puDS38BMOENdlU3gv0otv08iNvG0fDA&oh=23420b5ddaa2d1dd483e84a9c7e56a94&oe=5B87CCE5",
                R.drawable.music));
        arrayList.add(new Event("27.05.2018",
                "Grillty Pleasure la Manasia",
                "Grătarul este o formă de gătit care implică căldură uscată aplicată pe suprafața alimentelor, in acelasi timp fiind si o forma de socilizare des intalnita in cultura romaneasca.\n" +
                        "\n" +
                        "Avem un mix bun pentru ultima duminica din Mai. \n" +
                        "Ne hranim placerile nevinovate cu un gratar autentic romanesc si muzica de calitate. Avem terasa, umbra deasa si un aer de vacanta. \n" +
                        "\n" +
                        "Pastrama, carnaciorii, mititeii, frigaruile insotite cu legume la gratar, cartofi de tot felul, asortate cu un mix de muraturi ca sa fie ca la carte. \n" +
                        "\n" +
                        "Daytime Dj set - ESCU \n" +
                        "\n" +
                        "27 mai de Manasia 12 PM - 09 PM \n" +
                        "\n" +
                        "Manasia HUB\n" +
                        "real people | real stories \n" +
                        "Stelea Spataru 13\n" +
                        "Kid Friendly",
                "https://scontent.fotp3-1.fna.fbcdn.net/v/t15.0-10/29782337_161227571387958_4862142659721953280_n.jpg?_nc_cat=0&oh=cd43a6ff8db08d58050a33f65bbc37fa&oe=5BC4AB2F",
                R.drawable.hub));
        arrayList.add(new Event("01.06.2018",
                "AM LOC!",
                "Știm cât de important este să ai loc când ești LGBT. Cu surle, trâmbițe, bucurie în suflet și entuziasm te invităm pe 1 iunie, de la ora 16, la noul sediu MozaiQ la Manasia Hub, unde o să găsești o comunitate de oameni misto și muzică bună. Hai să-ți arătăm unde și cum lucrăm să facem România mai gay.\n" +
                        "\n" +
                        "Avem steaguri, avem tricouri, avem căni pline de curcubeu!",
                "https://scontent.fotp3-1.fna.fbcdn.net/v/t1.0-9/33186798_2161717270508608_244750867436142592_n.jpg?_nc_cat=0&oh=c6519d13586f5bc8ce2d0ce19a10fd84&oe=5BC4DF8E",
                R.drawable.hub));
        arrayList.add(new Event("02.06.2018",
                "Discotecă #Tropicalia la Manasia Hub",
                "\uD83C\uDF34 Discotecă #TROPICALIA la Manasia Hub\n" +
                        "\n" +
                        "Tradiționala primă ieșire anuală în decorul tropical al Bucureștiului ne va găsi sîmbătă, 2 iunie 2018, între orele 14.00 și 23.00, în grădina clorofilizată de la Manasia Hub, pentru o zi-lumină de dans, soare și cocteiluri exotice pe tot ceea ce a produs Electrecordul mai latino-american pentru vocile locale.\n" +
                        "\n" +
                        "\uD83C\uDF79\n" +
                        "Gardening\n" +
                        "Kid Friendly",
                "https://scontent.fotp3-1.fna.fbcdn.net/v/t1.0-9/33576478_1066399266845601_761101928934408192_n.jpg?_nc_cat=0&oh=99e5b3e8a90de317ad93a32d7a872247&oe=5B7677D1",
                R.drawable.music));

        return arrayList;
    }

    //filter posts by category
    //todo [IDEA] allow multiple categories
    private List<Event> filter(List<Event> list) {
        if (list == null) return null;

        ArrayList<Event> temp = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getCategory_image() == R.drawable.music && music) temp.add(list.get(i));
            else if (list.get(i).getCategory_image() == R.drawable.shop && shop)
                temp.add(list.get(i));
            else if (list.get(i).getCategory_image() == R.drawable.hub && hub)
                temp.add(list.get(i));
        }

        return temp;
    }

    public void musicToggle(View v) {
        if (music && (shop || hub)) {
            music = false;
        } else if (!music) {
            music = true;
        } else {
            music = true;
            shopToggle(null);
            hubToggle(null);
        }

        setFilterColor();
        refreshList();
        setPreferences();
    }

    public void shopToggle(View v) {
        if (shop && (music || hub)) {
            shop = false;
        } else if (!shop) {
            shop = true;
        } else {
            shop = true;
            musicToggle(null);
            hubToggle(null);
        }

        setFilterColor();
        refreshList();
        setPreferences();
    }

    public void hubToggle(View v) {
        if (hub && (music || shop)) {
            hub = false;
        } else if (!hub) {
            hub = true;
        } else {
            hub = true;
            musicToggle(null);
            shopToggle(null);
        }

        setFilterColor();
        refreshList();
        setPreferences();
    }

    private void setFilterColor() {
        if (music) music_toggle.setBackgroundColor(0xffFF4081);
        else music_toggle.setBackgroundColor(0xff9E9E9E);
        if (shop) shop_toggle.setBackgroundColor(0xffFF4081);
        else shop_toggle.setBackgroundColor(0xff9E9E9E);
        if (hub) hub_toggle.setBackgroundColor(0xffFF4081);
        else hub_toggle.setBackgroundColor(0xff9E9E9E);

        checkFiltersSet();
    }

    private void checkFiltersSet() {
        if (music && shop && hub) filters.setText("Filters");
        else filters.setText("Filters [set]");
    }

    //todo refactor this when we change the way we get event data
    //todo add flag for filters, to prevent hitting the database when we're just filtering
    private void refreshList() {
        events = (ArrayList<Event>) DBUtils.readDatabase(this);

        if (events != null) {
            listView.setAdapter(new EventAdapter(this, filter(events)));
        }
    }

    @Override
    public Loader<List<Event>> onCreateLoader(int id, Bundle args) {
        return new EventLoader(this, REMOTE_URL);
    }

    @Override
    public void onLoadFinished(Loader<List<Event>> loader, List<Event> data) {
        inputRemoteEventsIntoDatabase((ArrayList<Event>) data);
        events = (ArrayList<Event>) DBUtils.readDatabase(this);
        populateListView();
    }

    @Override
    public void onLoaderReset(Loader<List<Event>> loader) {
        listView.setAdapter(new EventAdapter(this, new ArrayList<>()));
    }
}

//todo [HIGH] implement notification handling; auto-eliminate notifications in the past
//todo add notification on new events added to remote database
//todo extract all strings into XML
//todo fix any warnings/errors
//todo [IDEA] feature hub shops to attract clients (i.e. give them space in the app or include their events)
//todo create event info subclass
//todo add info about the hub somewhere (on logo click?) and indicate it visually
//todo figure out data storage (firebase? facebook api?)
//todo create method to keep database clean (<100 entries?)
//todo [low] translate app (modify class, ensure input of event translations)
