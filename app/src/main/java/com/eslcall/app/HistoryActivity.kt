package com.eslcall.app

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val items = AlertHistoryStore.load(this)

        val layoutEmpty   = findViewById<LinearLayout>(R.id.layoutEmpty)
        val recycler      = findViewById<RecyclerView>(R.id.recyclerHistory)

        if (items.isEmpty()) {
            layoutEmpty.visibility  = View.VISIBLE
            recycler.visibility     = View.GONE
        } else {
            layoutEmpty.visibility  = View.GONE
            recycler.visibility     = View.VISIBLE
            recycler.layoutManager  = LinearLayoutManager(this)
            recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
            recycler.adapter        = HistoryAdapter(items)
        }
    }
}
