(function ($, rf) {
	
	rf.ui = rf.ui || {};
    
	rf.ui.InplaceSelect =  function(id, options) {
    	$super.constructor.call(this, id, options)
    	this.select = new rf.ui.Select(options.listCord, this, options);
    	this.selectItems = options.selectItems;
    	this.selValueInput = $(document.getElementById(options.selValueInput));
		this.openPopup = false; 

    }

	rf.ui.InplaceInput.extend(rf.ui.InplaceSelect);
	$.extend(rf.ui.InplaceSelect, rf.ui.SelectListener);
	
	var $super = rf.ui.InplaceSelect.$super;
	$.extend(rf.ui.InplaceSelect.prototype, ( function () {
		
		return{
			name : "inplaceSelect",
			
			getName: function() {
				return this.name;
			},
			
			geNamespace: function() {
				return this.namespace;
			},

			onshow: function() {
				if(this.openPopup) {
					this.select.show();
				}
				
				if(!this.openPopup) {
					this.openPopup = true;
				}
				
				$super.onshow.call(this);
			}, 
			
			onhide: function() {
				this.select.hide();
				this.openPopup = false;
			}, 
			
			processItem: function(event, element) {
				var key = element.attr("id");
				var value = this.getItemValue(key);
				this.saveItemValue(value);
				
				var label = this.getItemLabel(key);
				//inplace label
				this.setValue(label);
           		this.select.hide();

           		this.__setInputFocus();
			},
			
			getItemValue: function(key) {
				for(var i in this.selectItems) {
					var item = this.selectItems[i];
					if(item && item.id == key) {
						return item.value;
					}
				}
			}, 
			
			saveItemValue: function(value) {
				this.selValueInput.val(value);
			},
			
			getItemLabel: function(key) {
				for(var i in this.selectItems) {
					var item = this.selectItems[i];
					if(item && item.id == key) {
						return item.label;
					}
				}
			}, 
						
			__blurHandler: function(e) {
				var target = $(e.originalEvent.explicitOriginalTarget);
				if(!this.__isPopupList(target)) {
					$super.__blurHandler.call(this,e);
				} 				
       			return false;
       		},
       		
       		__isPopupList: function(target) {
       			var parentId = target.parents(".insel_list_cord").attr("id");
       			return (parentId && (parentId == this.select.__getId()));
       		}
		}
		
	})());

})(jQuery, window.RichFaces);
