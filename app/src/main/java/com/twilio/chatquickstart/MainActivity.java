package com.twilio.chatquickstart;

import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.twilio.chat.CallbackListener;
import com.twilio.chat.Channel;
import com.twilio.chat.ChannelListener;
import com.twilio.chat.ChatClient;
import com.twilio.chat.ErrorInfo;
import com.twilio.chat.Member;
import com.twilio.chat.Message;
import com.twilio.chat.StatusListener;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /*
       Change this URL to match the token URL for your Twilio Function
    */
    final static String SERVER_TOKEN_URL = "https://carnelian-aardvark-5364.twil.io/chat-token";

    final static String DEFAULT_CHANNEL_NAME = "general";
    final static String TAG = "TwilioChat";

    // Update this identity for each individual user, for instance after they login
    private String mIdentity = "CHAT_USER";

    private RecyclerView mMessagesRecyclerView;
    private MessagesAdapter mMessagesAdapter;
    private ArrayList<Message> mMessages = new ArrayList<>();

    private EditText mWriteMessageEditText;
    private Button mSendChatMessageButton;

    private ChatClient mChatClient;

    private Channel mGeneralChannel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMessagesRecyclerView = (RecyclerView) findViewById(R.id.messagesRecyclerView);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // for a chat app, show latest at the bottom
        layoutManager.setStackFromEnd(true);
        mMessagesRecyclerView.setLayoutManager(layoutManager);

        mMessagesAdapter = new MessagesAdapter();
        mMessagesRecyclerView.setAdapter(mMessagesAdapter);

        mWriteMessageEditText = (EditText) findViewById(R.id.writeMessageEditText);

        mSendChatMessageButton = (Button) findViewById(R.id.sendChatMessageButton);
        mSendChatMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mGeneralChannel != null) {
                    String messageBody = mWriteMessageEditText.getText().toString();
                    Message.Options options = Message.options().withBody(messageBody);
                    Log.d(TAG,"Message created");
                    mGeneralChannel.getMessages().sendMessage(options, new CallbackListener<Message>() {
                        @Override
                        public void onSuccess(Message message) {
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // need to modify user interface elements on the UI thread
                                    mWriteMessageEditText.setText("");
                                }
                            });
                        }
                    });
                }
            }
        });

        retrieveAccessTokenfromServer();
    }

    private void retrieveAccessTokenfromServer() {
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        String tokenURL = SERVER_TOKEN_URL + "?device=" + deviceId + "&identity=" + mIdentity;

        Ion.with(this)
                .load(tokenURL)
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        if (e == null) {
                            String accessToken = result.get("token").getAsString();

                            Log.d(TAG, "Retrieved access token from server: " + accessToken);

                            setTitle(mIdentity);

                            ChatClient.Properties.Builder builder = new ChatClient.Properties.Builder();
                            ChatClient.Properties props = builder.createProperties();
                            ChatClient.create(MainActivity.this,accessToken,props,mChatClientCallback);

                        } else {
                            Log.e(TAG,e.getMessage(),e);
                            Toast.makeText(MainActivity.this,
                                    R.string.error_retrieving_access_token, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
    }

    private void loadChannels() {
        mChatClient.getChannels().getChannel("2", new CallbackListener<Channel>() {
            @Override
            public void onSuccess(Channel channel) {
                if (channel != null) {
                    Log.d(TAG, "Joining Channel onSuccess loadChannels: " + DEFAULT_CHANNEL_NAME);
                    joinChannel(channel);
                } else {
                    Log.d(TAG, "Creating Channel: " + DEFAULT_CHANNEL_NAME);


                    mChatClient.getChannels().channelBuilder()
                            .withFriendlyName("general")
                            .withUniqueName("2")
                            .withType(Channel.ChannelType.PUBLIC)
                            .build(new CallbackListener<Channel>() {
                                @Override
                                public void onSuccess(Channel channel) {
                                    if (channel != null) {
                                        Log.d(TAG, "Joining Channel: " + DEFAULT_CHANNEL_NAME);

                                        joinChannel(channel);
                                    }
                                }

                                @Override
                                public void onError(ErrorInfo errorInfo) {
                                    Log.e(TAG,"Error creating channel: " + errorInfo.getMessage());
                                }
                            });

                }
            }

            @Override
            public void onError(ErrorInfo errorInfo) {
                Log.e(TAG,"Error retrieving channel: " + errorInfo.getMessage());
                mChatClient.getChannels().channelBuilder()
                        .withFriendlyName("general")
                        .withUniqueName("2")
                        .withType(Channel.ChannelType.PUBLIC)
                        .build(new CallbackListener<Channel>() {
                            @Override
                            public void onSuccess(Channel channel) {
                                if (channel != null) {
                                    Log.d(TAG, "Joining Channel: " + DEFAULT_CHANNEL_NAME);

                                    joinChannel(channel);
                                }
                            }

                            @Override
                            public void onError(ErrorInfo errorInfo) {
                                Log.e(TAG,"Error creating channel: " + errorInfo.getMessage());
                            }
                        });
            }

        });

    }

    private void joinChannel(final Channel channel) {

        Log.d(TAG, "Joining Channel: " + channel.getUniqueName());
        channel.join(new StatusListener() {
            @Override
            public void onSuccess() {
                mGeneralChannel = channel;
                Log.d(TAG, "Joined default channel");
                mGeneralChannel.addListener(mDefaultChannelListener);
            }

            @Override
            public void onError(ErrorInfo errorInfo) {
                Log.e(TAG, "Error joining channel: " + errorInfo.getMessage());
                Log.e(TAG, "Error joining channel: " + errorInfo.getStatus());
                Log.e(TAG, "Error joining channel: " + errorInfo.getCode());
                if (errorInfo.getMessage().equals("Member already exists"))
                {
                    mGeneralChannel = channel;
                    Log.d(TAG, "Joined default channel");
                    mGeneralChannel.addListener(mDefaultChannelListener);

                    mGeneralChannel.getMessagesCount(new CallbackListener<Long>() {
                        @Override
                        public void onError(ErrorInfo errorInfo) {
                            Log.d(TAG, "getMessagesCount:" + errorInfo.getMessage() );
                        }

                        @Override
                        public void onSuccess(Long aLong) {

                            channel.getMessages().getLastMessages(aLong.intValue(), new CallbackListener<List<Message>>() {
                                @Override
                                public void onSuccess(List<Message> messages) {
                                    for (Message message : messages) {
                                        mMessages.add(message);
                                        mMessagesAdapter.notifyDataSetChanged();
                                        Log.d(TAG, "Message Body: " + message.getMessageBody());
                                    }
                                }
                            });


                        }
                    });

                }

            }
        });

    }

    private CallbackListener<ChatClient> mChatClientCallback =
            new CallbackListener<ChatClient>() {
                @Override
                public void onSuccess(ChatClient chatClient) {
                    mChatClient = chatClient;
                    loadChannels();
                    Log.d(TAG, "Success creating Twilio Chat Client");
                }

                @Override
                public void onError(ErrorInfo errorInfo) {
                    Log.e(TAG,"Error creating Twilio Chat Client: " + errorInfo.getMessage());
                }
            };

    private ChannelListener mDefaultChannelListener = new ChannelListener() {


        @Override
        public void onMessageAdded(final Message message) {
            Log.d(TAG, "Message added");
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // need to modify user interface elements on the UI thread
                    mMessages.add(message);
                    mMessagesAdapter.notifyDataSetChanged();
                }
            });

        }

        @Override
        public void onMessageUpdated(Message message, Message.UpdateReason updateReason) {
            Log.d(TAG, "Message updated: " + message.getMessageBody());
        }

        @Override
        public void onMessageDeleted(Message message) {
            Log.d(TAG, "Message deleted");
        }

        @Override
        public void onMemberAdded(Member member) {
            Log.d(TAG, "Member added: " + member.getIdentity());
        }

        @Override
        public void onMemberUpdated(Member member, Member.UpdateReason updateReason) {
            Log.d(TAG, "Member updated: " + member.getIdentity());
        }

        @Override
        public void onMemberDeleted(Member member) {
            Log.d(TAG, "Member deleted: " + member.getIdentity());
        }

        @Override
        public void onTypingStarted(Channel channel, Member member) {
            Log.d(TAG, "Started Typing: " + member.getIdentity());
        }

        @Override
        public void onTypingEnded(Channel channel, Member member) {
            Log.d(TAG, "Ended Typing: " + member.getIdentity());
        }

        @Override
        public void onSynchronizationChanged(Channel channel) {

        }
    };


    class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {

            public TextView mMessageTextView;

            public ViewHolder(TextView textView) {
                super(textView);
                mMessageTextView = textView;
            }
        }

        public MessagesAdapter() {

        }

        @Override
        public MessagesAdapter
                .ViewHolder onCreateViewHolder(ViewGroup parent,
                                               int viewType) {
            TextView messageTextView = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_text_view, parent, false);
            return new ViewHolder(messageTextView);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Message message = mMessages.get(position);
            String messageText = String.format("%s: %s", message.getAuthor(), message.getMessageBody());
            holder.mMessageTextView.setText(messageText);

        }

        @Override
        public int getItemCount() {
            return mMessages.size();
        }
    }

    void createChannel()
    {
        mChatClient.getChannels().channelBuilder().withFriendlyName("general")
            .withType(Channel.ChannelType.PUBLIC)
            .build(new CallbackListener<Channel>() {
                @Override
                public void onSuccess(Channel channel) {
                    if (channel != null) {
                        Log.d(TAG,"Success creating channel");
                        joinChannel(channel);
                    }
                }

                @Override
                public void onError(ErrorInfo errorInfo) {
                    Log.e(TAG,"Error creating channel: " + errorInfo.getMessage());
                }
            });
    }

}
